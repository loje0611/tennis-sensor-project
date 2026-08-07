package io.github.loje0611.tennisdoc.core.sensor

/**
 * BLE 센서 하드웨어를 추상화하는 인터페이스.
 * [RealBleDataSource]와 [MockBleDataSource] 모두 이 계약을 따른다.
 */
interface SensorDataSource {
    fun connect()
    fun disconnect()
    fun release()
    fun sendCommand(cmd: String)
}
