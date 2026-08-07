package io.github.loje0611.tennisdoc.core.sensor

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

@SuppressLint("MissingPermission")
class BleManager(
    context: Context,
    private val onConnectionState: (BleConnectionState) -> Unit,
    private val onSensorPayload: (String) -> Unit,
) {
    private val appContext = context.applicationContext
    private val bluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var scanner: BluetoothLeScanner? = null
    @Volatile private var gatt: BluetoothGatt? = null
    private val scanInProgress = AtomicBoolean(false)
    private val connectInFlight = AtomicBoolean(false)

    @Volatile private var released = false
    @Volatile private var autoReconnect = true
    @Volatile private var reconnectAttempt = 0
    @Volatile private var lastDevice: BluetoothDevice? = null

    /** BLE 쓰기 직렬화: 동시 writeCharacteristic/writeDescriptor 호출 방지. */
    private val writeQueue = ConcurrentLinkedQueue<() -> Unit>()
    private val writePending = AtomicBoolean(false)

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (!connectInFlight.compareAndSet(false, true)) return
            stopScanInternal()
            val device = result.device
            Log.d(TAG, "스캔 결과: address=${device.address}, name=${device.name ?: result.scanRecord?.deviceName}")
            connectInternal(device)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "스캔 실패: errorCode=$errorCode")
            scanInProgress.set(false)
            connectInFlight.set(false)
            onConnectionState(BleConnectionState.Error(BleConnectionState.ErrorReason.ScanFailed))
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "GATT 연결됨 (status=$status), MTU 협상 요청")
                    reconnectAttempt = 0
                    lastDevice = gatt.device
                    onConnectionState(BleConnectionState.Connected)
                    val mtuOk = gatt.requestMtu(MTU_REQUEST)
                    if (!mtuOk) {
                        Log.w(TAG, "requestMtu($MTU_REQUEST) 즉시 실패 — 기본 MTU로 서비스 탐색")
                        gatt.discoverServices()
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "GATT 끊김 (status=$status)")
                    connectInFlight.set(false)
                    scanInProgress.set(false)
                    writeQueue.clear()
                    writePending.set(false)
                    cleanupGatt(gatt)
                    onConnectionState(BleConnectionState.Disconnected)
                    attemptReconnect()
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (this@BleManager.gatt != gatt) return
            val ok = status == BluetoothGatt.GATT_SUCCESS
            Log.d(TAG, "onMtuChanged mtu=$mtu status=$status (${if (ok) "OK" else "FAIL"}) → discoverServices()")
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "서비스 검색 실패: status=$status")
                gatt.disconnect()
                return
            }
            val service = gatt.getService(SERVICE_UUID)
            if (service == null) {
                Log.e(TAG, "서비스 없음: $SERVICE_UUID")
                gatt.disconnect()
                return
            }
            val characteristic = service.getCharacteristic(CHARACTERISTIC_UUID)
            if (characteristic == null) {
                Log.e(TAG, "Characteristic 없음: $CHARACTERISTIC_UUID")
                gatt.disconnect()
                return
            }

            val notifySet = gatt.setCharacteristicNotification(characteristic, true)
            if (!notifySet) {
                Log.e(TAG, "setCharacteristicNotification 실패")
                gatt.disconnect()
                return
            }

            val cccd = characteristic.getDescriptor(CCCD_UUID)
            if (cccd == null) {
                Log.e(TAG, "CCCD(0x2902) 디스크립터 없음")
                gatt.disconnect()
                return
            }
            enqueueWrite {
                @Suppress("DEPRECATION")
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                val writeOk = gatt.writeDescriptor(cccd)
                if (!writeOk) {
                    Log.e(TAG, "writeDescriptor(CCCD) 실패")
                    gatt.disconnect()
                }
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "CCCD 쓰기 성공: ${descriptor.uuid}")
            } else {
                Log.e(TAG, "CCCD 쓰기 실패: status=$status, uuid=${descriptor.uuid}")
            }
            drainWriteQueue()
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "Characteristic write 실패: status=$status")
            }
            drainWriteQueue()
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            dispatchNotify(characteristic, value)
        }

        @Suppress("DEPRECATION")
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            val legacy = characteristic.value ?: return
            dispatchNotify(characteristic, legacy)
        }

        private fun dispatchNotify(
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (characteristic.uuid != CHARACTERISTIC_UUID) return
            val text = value.toString(Charsets.UTF_8).trim()
            Log.d(TAG, "수신: $text")
            onSensorPayload(text)
        }
    }

    fun startScanAndConnect() {
        val adapter = bluetoothAdapter ?: run {
            Log.e(TAG, "BluetoothAdapter 없음")
            onConnectionState(BleConnectionState.Error(BleConnectionState.ErrorReason.BluetoothOff))
            return
        }
        if (!adapter.isEnabled) {
            Log.w(TAG, "블루투스 꺼짐")
            onConnectionState(BleConnectionState.Error(BleConnectionState.ErrorReason.BluetoothOff))
            return
        }
        if (gatt != null) {
            Log.w(TAG, "이미 GATT 세션 있음")
            return
        }
        if (!scanInProgress.compareAndSet(false, true)) {
            Log.w(TAG, "이미 스캔 중")
            return
        }
        released = false
        autoReconnect = true
        connectInFlight.set(false)

        onConnectionState(BleConnectionState.Scanning)

        val leScanner = adapter.bluetoothLeScanner
        scanner = leScanner

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(SERVICE_UUID))
                .build(),
            ScanFilter.Builder()
                .setDeviceName(DEVICE_NAME)
                .build(),
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            leScanner.startScan(filters, settings, scanCallback)
            Log.d(TAG, "BLE 스캔 시작 (필터: Service UUID OR Device Name)")

            mainHandler.postDelayed({
                if (scanInProgress.get() && gatt == null) {
                    Log.w(TAG, "스캔 타임아웃 ($SCAN_TIMEOUT_MS ms)")
                    stopScanInternal()
                    connectInFlight.set(false)
                    onConnectionState(BleConnectionState.Error(BleConnectionState.ErrorReason.ConnectionTimeout))
                }
            }, SCAN_TIMEOUT_MS)
        } catch (e: SecurityException) {
            Log.e(TAG, "스캔 권한 없음", e)
            scanInProgress.set(false)
            onConnectionState(BleConnectionState.Error(BleConnectionState.ErrorReason.PermissionDenied))
        }
    }

    fun disconnect() {
        autoReconnect = false
        stopScanInternal()
        scanInProgress.set(false)
        gatt?.disconnect()
    }

    fun release() {
        released = true
        autoReconnect = false
        mainHandler.removeCallbacksAndMessages(null)
        stopScanInternal()
        scanInProgress.set(false)
        connectInFlight.set(false)
        writeQueue.clear()
        writePending.set(false)
        val current = gatt
        gatt = null
        current?.disconnect()
        current?.close()
        onConnectionState(BleConnectionState.Disconnected)
    }

    private fun connectInternal(device: BluetoothDevice) {
        Log.d(TAG, "연결 시도: ${device.address}")
        lastDevice = device
        try {
            gatt = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                @Suppress("DEPRECATION")
                device.connectGatt(appContext, false, gattCallback)
            }

            mainHandler.postDelayed({
                if (connectInFlight.get() && gatt?.device?.address == device.address) {
                    val currentState = bluetoothManager.getConnectionState(device, BluetoothProfile.GATT)
                    if (currentState != BluetoothProfile.STATE_CONNECTED) {
                        Log.w(TAG, "연결 타임아웃 ($CONNECT_TIMEOUT_MS ms)")
                        gatt?.disconnect()
                        gatt?.close()
                        gatt = null
                        connectInFlight.set(false)
                        onConnectionState(BleConnectionState.Error(BleConnectionState.ErrorReason.ConnectionTimeout))
                        attemptReconnect()
                    }
                }
            }, CONNECT_TIMEOUT_MS)
        } catch (e: SecurityException) {
            Log.e(TAG, "connectGatt 권한 오류", e)
            connectInFlight.set(false)
            scanInProgress.set(false)
            onConnectionState(BleConnectionState.Error(BleConnectionState.ErrorReason.PermissionDenied))
        }
    }

    private fun attemptReconnect() {
        if (!autoReconnect || released) return
        if (reconnectAttempt >= MAX_RECONNECT_ATTEMPTS) {
            Log.w(TAG, "최대 재연결 시도 횟수 도달 ($MAX_RECONNECT_ATTEMPTS)")
            onConnectionState(BleConnectionState.Error(BleConnectionState.ErrorReason.MaxReconnectReached))
            return
        }
        reconnectAttempt++
        val delayMs = (RECONNECT_BASE_DELAY_MS * reconnectAttempt).coerceAtMost(RECONNECT_MAX_DELAY_MS)
        Log.d(TAG, "자동 재연결 시도 #$reconnectAttempt (${delayMs}ms 후)")

        mainHandler.postDelayed({
            if (released || !autoReconnect) return@postDelayed
            val device = lastDevice
            if (device != null && !connectInFlight.get()) {
                connectInFlight.set(true)
                connectInternal(device)
            } else {
                startScanAndConnect()
            }
        }, delayMs)
    }

    private fun stopScanInternal() {
        val sc = scanner ?: return
        try {
            sc.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.w(TAG, "stopScan failed", e)
        }
        scanInProgress.set(false)
    }

    /** BLE 쓰기 큐에 작업 추가. 이전 쓰기 완료 후 다음 쓰기 실행. */
    private fun enqueueWrite(block: () -> Unit) {
        writeQueue.add(block)
        if (writePending.compareAndSet(false, true)) {
            val next = writeQueue.poll()
            next?.invoke()
        }
    }

    private fun drainWriteQueue() {
        val next = writeQueue.poll()
        if (next != null) {
            next.invoke()
        } else {
            writePending.set(false)
        }
    }

    fun sendCommand(cmd: String) {
        val currentGatt = this.gatt ?: run {
            Log.w(TAG, "sendCommand: GATT is null")
            return
        }
        val service = currentGatt.getService(SERVICE_UUID)
        if (service == null) {
            Log.w(TAG, "sendCommand: Service not found")
            return
        }
        val characteristic = service.getCharacteristic(CHARACTERISTIC_UUID)
        if (characteristic == null) {
            Log.w(TAG, "sendCommand: Characteristic not found")
            return
        }

        val data = cmd.toByteArray(Charsets.UTF_8)
        enqueueWrite {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                currentGatt.writeCharacteristic(
                    characteristic,
                    data,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
                )
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = data
                @Suppress("DEPRECATION")
                currentGatt.writeCharacteristic(characteristic)
            }
            Log.d(TAG, "sendCommand: Sent '$cmd'")
        }
    }

    private fun cleanupGatt(gatt: BluetoothGatt) {
        if (this.gatt == gatt) {
            this.gatt = null
        }
        try {
            gatt.close()
        } catch (e: Exception) {
            Log.w(TAG, "GATT close failed", e)
        }
    }

    companion object {
        private const val TAG = "BleManager"
        private const val DEVICE_NAME = "Tennis_Sensor_V1"
        private val SERVICE_UUID =
            UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
        private val CHARACTERISTIC_UUID =
            UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
        private val CCCD_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private const val MTU_REQUEST = 512
        private const val SCAN_TIMEOUT_MS = 15_000L
        private const val CONNECT_TIMEOUT_MS = 10_000L
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val RECONNECT_BASE_DELAY_MS = 2_000L
        private const val RECONNECT_MAX_DELAY_MS = 15_000L
    }
}
