package io.github.loje0611.tennisdoc.sensor

import io.github.loje0611.tennisdoc.BleConnectionState

/**
 * BLE 하드웨어 없이 가상 연결 상태만 발생시키는 [SensorDataSource] 구현체.
 * 실제 센서 데이터는 [MockSwingDataGenerator]가 별도로 생성하며,
 * 이 클래스는 연결/해제 라이프사이클만 담당한다.
 */
class MockBleDataSource(
    private val onConnectionState: (BleConnectionState) -> Unit,
) : SensorDataSource {

    override fun connect() {
        onConnectionState(BleConnectionState.Connected)
    }

    override fun disconnect() {
        onConnectionState(BleConnectionState.Disconnected)
    }

    override fun release() {
        disconnect()
    }

    override fun sendCommand(cmd: String) { /* mock — no-op */ }
}
