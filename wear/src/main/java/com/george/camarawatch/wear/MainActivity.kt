package com.george.camarawatch.wear

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.lifecycleScope
import androidx.wear.ambient.AmbientLifecycleObserver
import com.george.camarawatch.shared.CameraCommand
import com.george.camarawatch.shared.CameraControls
import com.george.camarawatch.wear.preview.TcpPreviewClient
import com.george.camarawatch.wear.ui.WatchScreen
import com.george.camarawatch.wear.wear.PreviewInfo
import com.george.camarawatch.wear.wear.WatchWearBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : ComponentActivity() {
    private val status = MutableStateFlow("Buscando el teléfono…")
    private val recording = MutableStateFlow(false)
    private val preview = MutableStateFlow<ImageBitmap?>(null)
    private val usingWifi = MutableStateFlow(false)
    private val controls = MutableStateFlow(CameraControls())
    private val ambient = MutableStateFlow(false)
    private val timerSeconds = MutableStateFlow(0)
    private val countdown = MutableStateFlow(-1)
    private var suppressCountdown = false

    private lateinit var bridge: WatchWearBridge
    private var previewJob: Job? = null
    private var latestInfo: PreviewInfo? = null
    private val previewEpoch = AtomicInteger(0)

    @Volatile private var screenPaused = false

    @Volatile private var inAmbient = false

    private val ambientCallback = object : AmbientLifecycleObserver.AmbientLifecycleCallback {
        override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
            inAmbient = true
            ambient.value = true
            stopPreviewTransport()
        }

        override fun onExitAmbient() {
            inAmbient = false
            ambient.value = false
            resumePreviewIfInteractive()
        }

        override fun onUpdateAmbient() = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(AmbientLifecycleObserver(this, ambientCallback))
        bridge = WatchWearBridge(
            context = this,
            onPreviewInfo = { info ->
                latestInfo = info
                status.value = info.status
                recording.value = info.recording
                controls.value = info.controls
                applyCountdown(info.timer.remaining)
                resumePreviewIfInteractive()
            },
            onStatus = { snapshot ->
                status.value = snapshot.message
                recording.value = snapshot.recording
                snapshot.controls?.let { controls.value = it }
                applyCountdown(snapshot.timer.remaining)
            },
            onFrame = { bytes ->
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@WatchWearBridge
                preview.value = bitmap.asImageBitmap()
            },
        )
        timerSeconds.value = savedTimer()
        setContent {
            WatchScreen(
                status = status,
                recording = recording,
                preview = preview,
                usingWifi = usingWifi,
                controls = controls,
                ambient = ambient,
                timerSeconds = timerSeconds,
                countdown = countdown,
                onCommand = ::send,
                onShutter = ::shutter,
                onTimer = ::selectTimer,
                onCancelCountdown = ::cancelCountdown,
            )
        }
    }

    private fun savedTimer(): Int {
        val stored = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_TIMER, 0)
        return if (stored == 3 || stored == 10) stored else 0
    }

    private fun selectTimer(seconds: Int) {
        val safe = if (seconds == 3 || seconds == 10) seconds else 0
        timerSeconds.value = safe
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_TIMER, safe).apply()
        send(CameraCommand.SetTimer(safe))
    }

    private fun shutter(video: Boolean) {
        if (countdown.value > 0) {
            cancelCountdown()
            return
        }
        if (video && recording.value) {
            send(CameraCommand.StopRecord)
            return
        }
        val seconds = timerSeconds.value
        if (seconds > 0) {
            suppressCountdown = false
            countdown.value = seconds
            send(CameraCommand.ArmCountdown(video = video, seconds = seconds))
        } else if (video) {
            send(CameraCommand.StartRecord)
        } else {
            send(CameraCommand.TakePhoto)
        }
    }

    private fun cancelCountdown() {
        suppressCountdown = true
        countdown.value = -1
        send(CameraCommand.CancelCountdown)
    }

    private fun applyCountdown(remaining: Int) {
        if (remaining <= 0) {
            suppressCountdown = false
            countdown.value = -1
        } else if (!suppressCountdown) {
            countdown.value = remaining
        }
    }

    override fun onResume() {
        super.onResume()
        screenPaused = false
        bridge.start()
        lifecycleScope.launch {
            runCatching { bridge.refreshPreviewInfo() }
            resumePreviewIfInteractive()
        }
    }

    override fun onPause() {
        screenPaused = true
        stopPreviewTransport()
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

    private fun resumePreviewIfInteractive() {
        if (inAmbient || screenPaused) return
        ensurePreview()
    }

    private fun stopPreviewTransport() {
        previewEpoch.incrementAndGet()
        previewJob?.cancel()
        previewJob = null
        usingWifi.value = false
    }

    private fun ensurePreview() {
        if (inAmbient || screenPaused) return
        if (previewJob?.isActive == true) return
        val generation = previewEpoch.get()
        previewJob = lifecycleScope.launch(Dispatchers.IO) {
            while (isActive && previewEpoch.get() == generation && !inAmbient && !screenPaused) {
                val info = latestInfo
                val ip = info?.ip
                val shouldStop = {
                    previewEpoch.get() != generation || inAmbient || screenPaused || !isActive
                }
                val connectedWifi = if (!ip.isNullOrBlank()) {
                    status.value = "Conectando vista previa Wi‑Fi…"
                    TcpPreviewClient.tryConnect(
                        ip = ip,
                        port = info.port,
                        onFrame = { bytes ->
                            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (bitmap != null) preview.value = bitmap.asImageBitmap()
                        },
                        shouldStop = shouldStop,
                    )
                } else {
                    false
                }
                if (shouldStop()) break
                if (connectedWifi) {
                    usingWifi.value = true
                    continue
                }
                usingWifi.value = false
                status.value = "Vista previa por Wear OS…"
                val channelOk = runCatching {
                    bridge.openPreviewChannel(shouldStop = shouldStop)
                    true
                }.getOrDefault(false)
                if (shouldStop()) break
                if (!channelOk) {
                    withContext(Dispatchers.Main) {
                        if (status.value.startsWith("Vista previa") || status.value.startsWith("Conectando")) {
                            status.value = "Esperando el teléfono…"
                        }
                    }
                    delay(1_500)
                }
            }
        }
    }

    private fun vibrate() {
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    companion object {
        private const val PREFS = "camara_watch"
        private const val KEY_TIMER = "timer_seconds"
    }
}
