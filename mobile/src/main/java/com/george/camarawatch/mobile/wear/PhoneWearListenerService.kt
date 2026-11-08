package com.george.camarawatch.mobile.wear

import android.content.Intent
import android.os.Build
import com.george.camarawatch.mobile.camera.CameraForegroundService
import com.george.camarawatch.shared.CameraCommand
import com.george.camarawatch.shared.Protocol
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class PhoneWearListenerService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != Protocol.COMMAND_PATH) return
        val wire = String(messageEvent.data, Charsets.UTF_8)
        if (CameraCommand.fromWire(wire) == null) return
        startCameraService(wire)
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

    private fun startCameraService(wire: String?) {
        val intent = Intent(this, CameraForegroundService::class.java).apply {
            if (!wire.isNullOrBlank()) putExtra(CameraForegroundService.EXTRA_COMMAND, wire)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
