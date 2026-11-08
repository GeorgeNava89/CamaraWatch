package com.george.camarawatch.mobile.wear

import android.content.Context
import android.util.Log
import com.george.camarawatch.mobile.preview.PreviewHub
import com.george.camarawatch.shared.CameraControls
import com.george.camarawatch.shared.PhoneStatus
import com.george.camarawatch.shared.PreviewCodec
import com.george.camarawatch.shared.Protocol
import com.george.camarawatch.shared.encodeSnapshot
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

class PhoneWearBridge(
    context: Context,
    private val hub: PreviewHub,
    private val scope: CoroutineScope,
    private val onChannelClients: (Int) -> Unit,
) {
    private val messageClient = Wearable.getMessageClient(context)
    private val dataClient = Wearable.getDataClient(context)
    private val nodeClient = Wearable.getNodeClient(context)
    private val channelClient = Wearable.getChannelClient(context)
    private val channelJobs = ConcurrentHashMap<String, Job>()

    suspend fun publishPreviewInfo(
        ip: String?,
        port: Int,
        detail: String,
        recording: Boolean,
        controls: CameraControls,
    ) {
        val request = PutDataMapRequest.create(Protocol.PREVIEW_INFO_PATH).apply {
            dataMap.putString(Protocol.KEY_IP, ip.orEmpty())
            dataMap.putInt(Protocol.KEY_PORT, port)
            dataMap.putString(Protocol.KEY_STATUS, detail)
            dataMap.putBoolean(Protocol.KEY_RECORDING, recording)
            dataMap.putLong(Protocol.KEY_TS, System.currentTimeMillis())
            dataMap.putString(Protocol.KEY_FACING, controls.facing.name)
            dataMap.putFloat(Protocol.KEY_ZOOM, controls.zoomRatio)
            dataMap.putFloat(Protocol.KEY_ZOOM_MIN, controls.minZoom)
            dataMap.putFloat(Protocol.KEY_ZOOM_MAX, controls.maxZoom)
            dataMap.putString(Protocol.KEY_FLASH, controls.flashMode.name)
            dataMap.putBoolean(Protocol.KEY_FLASH_SUPPORTED, controls.flashSupported)
            dataMap.putBoolean(Protocol.KEY_TORCH_SUPPORTED, controls.torchSupported)
            dataMap.putInt(Protocol.KEY_EXPOSURE, controls.exposureIndex)
            dataMap.putInt(Protocol.KEY_EXPOSURE_MIN, controls.exposureMin)
            dataMap.putInt(Protocol.KEY_EXPOSURE_MAX, controls.exposureMax)
            dataMap.putFloat(Protocol.KEY_EXPOSURE_STEP, controls.exposureStep)
            dataMap.putBoolean(Protocol.KEY_CAN_SWITCH, controls.canSwitchCamera)
        }
        dataClient.putDataItem(request.asPutDataRequest().setUrgent()).await()
    }

    suspend fun broadcastStatus(
        status: PhoneStatus,
        detail: String,
        recording: Boolean,
        controls: CameraControls,
    ) {
        val payload = encodeSnapshot(status, detail, recording, controls).toByteArray(Charsets.UTF_8)
        val nodes = nodeClient.connectedNodes.await()
        nodes.forEach { node ->
            runCatching {
                messageClient.sendMessage(node.id, Protocol.STATUS_PATH, payload).await()
            }
        }
    }

    fun onChannelOpened(channel: ChannelClient.Channel) {
        if (channel.path != Protocol.PREVIEW_CHANNEL_PATH) return
        val key = channel.nodeId
        channelJobs[key]?.cancel()
        channelJobs[key] = scope.launch(Dispatchers.IO) {
            var stream: OutputStream? = null
            try {
                stream = channelClient.getOutputStream(channel).await()
                onChannelClients(channelJobs.size)
                var last: ByteArray? = null
                while (isActive) {
                    val frame = hub.latestOrNull()
                    if (frame != null && frame !== last) {
                        PreviewCodec.writeFrame(stream, frame)
                        last = frame
                    }
                    delay(Protocol.PREVIEW_MIN_INTERVAL_MS)
                }
            } catch (error: Exception) {
                Log.d(TAG, "Canal Wear cerrado: ${error.message}")
            } finally {
                runCatching { stream?.close() }
                runCatching { channelClient.close(channel) }
                channelJobs.remove(key)
                onChannelClients(channelJobs.size)
            }
        }
    }

    fun onChannelClosed(channel: ChannelClient.Channel) {
        channelJobs.remove(channel.nodeId)?.cancel()
        onChannelClients(channelJobs.size)
    }

    fun close() {
        channelJobs.values.forEach { it.cancel() }
        channelJobs.clear()
        onChannelClients(0)
    }

    companion object {
        private const val TAG = "PhoneWearBridge"
    }
}
