package com.george.camarawatch.wear.wear

import android.content.Context
import android.util.Log
import com.george.camarawatch.shared.CameraCommand
import com.george.camarawatch.shared.PreviewCodec
import com.george.camarawatch.shared.Protocol
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

data class PreviewInfo(
    val ip: String?,
    val port: Int,
    val status: String,
    val recording: Boolean,
)

class WatchWearBridge(
    context: Context,
    private val onPreviewInfo: (PreviewInfo) -> Unit,
    private val onStatus: (String, Boolean) -> Unit,
    private val onFrame: (ByteArray) -> Unit,
) : DataClient.OnDataChangedListener, MessageClient.OnMessageReceivedListener {

    private val messageClient = Wearable.getMessageClient(context)
    private val dataClient = Wearable.getDataClient(context)
    private val nodeClient = Wearable.getNodeClient(context)
    private val channelClient = Wearable.getChannelClient(context)

    fun start() {
        dataClient.addListener(this)
        messageClient.addListener(this)
    }

    fun stop() {
        dataClient.removeListener(this)
        messageClient.removeListener(this)
    }

    suspend fun refreshPreviewInfo() {
        val items = dataClient.dataItems.await()
        try {
            items.forEach { item ->
                if (item.uri.path == Protocol.PREVIEW_INFO_PATH) {
                    onPreviewInfo(item.toPreviewInfo())
                }
            }
        } finally {
            items.release()
        }
    }

    suspend fun sendCommand(command: CameraCommand) {
        val payload = command.name.toByteArray(Charsets.UTF_8)
        val nodes = nodeClient.connectedNodes.await()
        if (nodes.isEmpty()) {
            throw IllegalStateException("El teléfono no está conectado")
        }
        nodes.forEach { node ->
            messageClient.sendMessage(node.id, Protocol.COMMAND_PATH, payload).await()
        }
    }

    suspend fun openPreviewChannel(shouldStop: () -> Boolean) {
        val nodes = nodeClient.connectedNodes.await()
        val phone = nodes.firstOrNull() ?: throw IllegalStateException("Sin nodo Wear")
        val channel = channelClient.openChannel(phone.id, Protocol.PREVIEW_CHANNEL_PATH).await()
        val input = channelClient.getInputStream(channel).await()
        try {
            while (!shouldStop()) {
                val jpeg = PreviewCodec.readFrame(input)
                onFrame(jpeg)
            }
        } finally {
            runCatching { input.close() }
            runCatching { channelClient.close(channel) }
        }
    }

    override fun onDataChanged(events: DataEventBuffer) {
        try {
            events.forEach { event ->
                if (event.type == DataEvent.TYPE_CHANGED &&
                    event.dataItem.uri.path == Protocol.PREVIEW_INFO_PATH
                ) {
                    onPreviewInfo(event.dataItem.toPreviewInfo())
                }
            }
        } finally {
            events.release()
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != Protocol.STATUS_PATH) return
        val text = String(messageEvent.data, Charsets.UTF_8)
        val parts = text.split('|')
        val status = parts.getOrNull(1) ?: text
        val recording = parts.getOrNull(2)?.toBooleanStrictOrNull() ?: false
        onStatus(status, recording)
    }

    companion object {
        private const val TAG = "WatchWearBridge"
        fun log(message: String) = Log.d(TAG, message)
    }
}

private fun com.google.android.gms.wearable.DataItem.toPreviewInfo(): PreviewInfo {
    val map = DataMapItem.fromDataItem(this).dataMap
    val ip = map.getString(Protocol.KEY_IP).orEmpty().ifBlank { null }
    return PreviewInfo(
        ip = ip,
        port = map.getInt(Protocol.KEY_PORT, Protocol.PREVIEW_PORT),
        status = map.getString(Protocol.KEY_STATUS).orEmpty().ifBlank { "Cámara lista." },
        recording = map.getBoolean(Protocol.KEY_RECORDING, false),
    )
}
