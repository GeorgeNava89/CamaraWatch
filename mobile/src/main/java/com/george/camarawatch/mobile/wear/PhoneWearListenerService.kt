package com.george.camarawatch.mobile.wear

import android.content.Intent
import android.os.Build
import com.george.camarawatch.mobile.CommandBus
import com.george.camarawatch.mobile.camera.CameraForegroundService
import com.george.camarawatch.shared.CameraCommand
import com.george.camarawatch.shared.Protocol
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class PhoneWearListenerService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != Protocol.COMMAND_PATH) return
        val command = CameraCommand.fromWire(String(messageEvent.data, Charsets.UTF_8)) ?: return
        CommandBus.emit(command)
        startCameraService(command)
    }

    override fun onChannelOpened(channel: ChannelClient.Channel) {
        if (channel.path == Protocol.PREVIEW_CHANNEL_PATH) {
            CameraForegroundService.pendingChannel = channel
            startCameraService(null)
        }
    }

    override fun onChannelClosed(channel: ChannelClient.Channel, closeReason: Int, appSpecificErrorCode: Int) {
        CameraForegroundService.onExternalChannelClosed(channel)
    }

    private fun startCameraService(command: CameraCommand?) {
        val intent = Intent(this, CameraForegroundService::class.java).apply {
            if (command != null) putExtra(CameraForegroundService.EXTRA_COMMAND, command.name)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
