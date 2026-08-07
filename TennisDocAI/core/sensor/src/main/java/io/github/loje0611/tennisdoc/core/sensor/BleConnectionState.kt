package io.github.loje0611.tennisdoc.core.sensor

sealed class BleConnectionState {
    data object Disconnected : BleConnectionState()
    data object Scanning : BleConnectionState()
    data object Connected : BleConnectionState()

    data class Error(val reason: ErrorReason) : BleConnectionState()

    enum class ErrorReason {
        BluetoothOff,
        PermissionDenied,
        ScanFailed,
        ConnectionTimeout,
        MaxReconnectReached,
    }

    val isConnected: Boolean get() = this is Connected
    val isDisconnectedOrError: Boolean get() = this is Disconnected || this is Error
}
