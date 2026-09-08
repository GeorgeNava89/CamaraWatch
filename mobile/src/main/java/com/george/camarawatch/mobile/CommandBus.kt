package com.george.camarawatch.mobile

import com.george.camarawatch.shared.CameraCommand
import com.george.camarawatch.shared.PhoneStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

object CommandBus {
    private val _commands = MutableSharedFlow<CameraCommand>(extraBufferCapacity = 32)
    val commands: SharedFlow<CameraCommand> = _commands.asSharedFlow()

    private val _status = MutableStateFlow(PhoneStatus.STOPPED)
    val status: StateFlow<PhoneStatus> = _status.asStateFlow()

    private val _statusDetail = MutableStateFlow(PhoneStatus.STOPPED.message)
    val statusDetail: StateFlow<String> = _statusDetail.asStateFlow()

    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording.asStateFlow()

    private val _wifiEndpoint = MutableStateFlow<String?>(null)
    val wifiEndpoint: StateFlow<String?> = _wifiEndpoint.asStateFlow()

    private val _previewClients = MutableStateFlow(0)
    val previewClients: StateFlow<Int> = _previewClients.asStateFlow()

    private val _serviceRunning = MutableStateFlow(false)
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    fun emit(command: CameraCommand) {
        _commands.tryEmit(command)
    }

    fun setStatus(status: PhoneStatus, detail: String = status.message) {
        _status.value = status
        _statusDetail.value = detail
    }

    fun setRecording(value: Boolean) {
        _recording.value = value
    }

    fun setWifiEndpoint(value: String?) {
        _wifiEndpoint.value = value
    }

    fun setPreviewClients(value: Int) {
        _previewClients.value = value
    }

    fun setServiceRunning(value: Boolean) {
        _serviceRunning.value = value
    }
}
