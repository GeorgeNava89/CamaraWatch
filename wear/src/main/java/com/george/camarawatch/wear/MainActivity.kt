package com.george.camarawatch.wear

import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.lifecycleScope
import com.george.camarawatch.shared.CameraCommand
import com.george.camarawatch.wear.preview.TcpPreviewClient
import com.george.camarawatch.wear.ui.WatchScreen
import com.george.camarawatch.wear.wear.PreviewInfo
import com.george.camarawatch.wear.wear.WatchWearBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val status = MutableStateFlow("Buscando el teléfono…")
    private val recording = MutableStateFlow(false)
    private val preview = MutableStateFlow<ImageBitmap?>(null)
    private val usingWifi = MutableStateFlow(false)

    private lateinit var bridge: WatchWearBridge
    private var previewJob: Job? = null
    private var latestInfo: PreviewInfo? = null
    private var stopPreview = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        bridge = WatchWearBridge(
            context = this,
            onPreviewInfo = { info ->
                latestInfo = info
                status.value = info.status
                recording.value = info.recording
                ensurePreview()
            },
            onStatus = { text, isRecording ->
                status.value = text
                recording.value = isRecording
            },
            onFrame = { bytes ->
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@WatchWearBridge
                preview.value = bitmap.asImageBitmap()
            },
        )
        setContent {
            WatchScreen(
                status = status,
                recording = recording,
                preview = preview,
                usingWifi = usingWifi,
                onFoto = { send(CameraCommand.TAKE_PHOTO) },
                onGrabar = {
                    if (recording.value) send(CameraCommand.STOP_RECORD) else send(CameraCommand.START_RECORD)
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        bridge.start()
        lifecycleScope.launch {
            runCatching { bridge.refreshPreviewInfo() }
            ensurePreview()
        }
    }

    override fun onPause() {
        stopPreview = true
        previewJob?.cancel()
        previewJob = null
        bridge.stop()
        super.onPause()
    }

    private fun send(command: CameraCommand) {
        vibrate()
        lifecycleScope.launch {
            runCatching { bridge.sendCommand(command) }
                .onFailure { status.value = it.message ?: "Sin conexión con el teléfono" }
        }
    }

    private fun ensurePreview() {
        if (previewJob?.isActive == true) return
        stopPreview = false
        previewJob = lifecycleScope.launch(Dispatchers.IO) {
            while (isActive && !stopPreview) {
                val info = latestInfo
                val ip = info?.ip
                val connectedWifi = if (!ip.isNullOrBlank()) {
                    status.value = "Conectando vista previa Wi‑Fi…"
                    TcpPreviewClient.tryConnect(
                        ip = ip,
                        port = info.port,
                        onFrame = { bytes ->
                            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (bitmap != null) preview.value = bitmap.asImageBitmap()
                        },
                        shouldStop = { stopPreview || !isActive },
                    )
                } else {
                    false
                }
                if (connectedWifi) {
                    usingWifi.value = true
                    continue
                }
                usingWifi.value = false
                status.value = "Vista previa por Wear OS…"
                val channelOk = runCatching {
                    bridge.openPreviewChannel(shouldStop = { stopPreview || !isActive })
                    true
                }.getOrElse {
                    false
                }
                if (!channelOk) {
                    withContext(Dispatchers.Main) {
                        if (status.value.startsWith("Vista previa") || status.value.startsWith("Conectando")) {
                            status.value = "Esperando el teléfono…"
                        }
                    }
                    kotlinx.coroutines.delay(1_500)
                }
            }
        }
    }

    private fun vibrate() {
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
