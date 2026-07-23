package com.example.swingsenseai.sensor

import android.content.Context
import com.example.swingsenseai.BleConnectionState
import com.example.swingsenseai.BleManager

/**
 * 실제 BLE(ESP32) 센서에 연결하는 [SensorDataSource] 구현체.
 * 기존 [BleManager]를 래핑하여 인터페이스를 맞춘다.
 */
class RealBleDataSource(
    context: Context,
    onConnectionState: (BleConnectionState) -> Unit,
    onSensorPayload: (String) -> Unit,
) : SensorDataSource {

    private val ble = BleManager(context, onConnectionState, onSensorPayload)

    override fun connect() = ble.startScanAndConnect()
    override fun disconnect() = ble.disconnect()
    override fun release() = ble.release()
    override fun sendCommand(cmd: String) = ble.sendCommand(cmd)
}
