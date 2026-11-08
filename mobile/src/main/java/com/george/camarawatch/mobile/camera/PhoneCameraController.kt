package com.george.camarawatch.mobile.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.ZoomState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.george.camarawatch.mobile.preview.PreviewHub
import com.george.camarawatch.shared.CameraControls
import com.george.camarawatch.shared.CameraFacing
import com.george.camarawatch.shared.FlashMode
import com.george.camarawatch.shared.PhoneStatus
import com.george.camarawatch.shared.Protocol
import com.george.camarawatch.shared.formatZoom
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs
import kotlin.math.max

class PhoneCameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val hub: PreviewHub,
    private val onStatus: (PhoneStatus, String) -> Unit,
    private val onRecording: (Boolean) -> Unit,
    private val onControls: (CameraControls) -> Unit,
) {
    private val mainExecutor: Executor = ContextCompat.getMainExecutor(context)
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var provider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var finalizing = false
    private var boundCamera: Camera? = null
    private var surfaceProvider: Preview.SurfaceProvider? = null
    private val lastFrameMs = AtomicLong(0L)
    private var bound = false
    private var facing = CameraFacing.BACK
    private var requestedFlash = FlashMode.OFF
    private var flashSupported = false
    private var torchSupported = false
    private var canSwitchCamera = false
    private var minZoom = 1f
    private var maxZoom = 1f
    private var currentZoom = 1f
    private var targetZoom = 1f
    private var zoomInitialized = false
    private var exposureIndex = 0
    private var exposureMin = 0
    private var exposureMax = 0
    private var exposureStepEv = 0f
    @Volatile private var mirrorFront = false
    private var lastPublishedKey: String? = null
    private var zoomLive: androidx.lifecycle.LiveData<ZoomState>? = null

    val previewUseCase: Preview = Preview.Builder().build()

    private val zoomObserver = Observer<ZoomState> { state ->
        minZoom = state.minZoomRatio
        maxZoom = state.maxZoomRatio
        currentZoom = state.zoomRatio
        if (!zoomInitialized) {
            zoomInitialized = true
            val initial = 1f.coerceIn(minZoom, maxZoom)
            targetZoom = initial
            if (abs(state.zoomRatio - initial) > 0.05f) {
                boundCamera?.cameraControl?.setZoomRatio(initial)
            }
        }
        publishControls()
    }

    suspend fun start() {
        val cameraProvider = ProcessCameraProvider.getInstance(context).await(mainExecutor)
        provider = cameraProvider
        facing = if (cameraProvider.hasLens(CameraSelector.DEFAULT_BACK_CAMERA) ||
            !cameraProvider.hasLens(CameraSelector.DEFAULT_FRONT_CAMERA)
        ) {
            CameraFacing.BACK
        } else {
            CameraFacing.FRONT
        }
        bindNow()
        onStatus(PhoneStatus.WAITING_WATCH, PhoneStatus.WAITING_WATCH.message)
    }

    fun attachPreview(provider: Preview.SurfaceProvider) {
        surfaceProvider = provider
        previewUseCase.setSurfaceProvider(provider)
    }

    fun takePhoto() {
        val capture = imageCapture ?: run {
            onStatus(PhoneStatus.CAMERA_UNAVAILABLE, PhoneStatus.CAMERA_UNAVAILABLE.message)
            return
        }
        onStatus(PhoneStatus.TAKING_PHOTO, PhoneStatus.TAKING_PHOTO.message)
        val name = fileName("jpg")
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/${Protocol.PICTURES_FOLDER}",
                )
            }
        }
        val options = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values,
        ).build()
        capture.takePicture(
            options,
            mainExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    onStatus(PhoneStatus.PHOTO_SAVED, PhoneStatus.PHOTO_SAVED.message)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Foto fallida", exception)
                    onStatus(
                        PhoneStatus.PHOTO_ERROR,
                        "${PhoneStatus.PHOTO_ERROR.message} ${exception.message ?: ""}".trim(),
                    )
                }
            },
        )
    }

    fun startRecording() {
        if (recording != null || finalizing) return
        val capture = videoCapture ?: run {
            onStatus(PhoneStatus.CAMERA_UNAVAILABLE, PhoneStatus.CAMERA_UNAVAILABLE.message)
            return
        }
        val name = fileName("mp4")
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Video.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_MOVIES}/${Protocol.MOVIES_FOLDER}",
                )
            }
        }
        val output = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        ).setContentValues(values).build()

        val pending = capture.output.prepareRecording(context, output)
        recording = try {
            pending.withAudioEnabled().start(mainExecutor, ::onVideoEvent)
        } catch (error: SecurityException) {
            pending.start(mainExecutor, ::onVideoEvent)
        }
        onRecording(true)
        onStatus(PhoneStatus.RECORDING, recordingDetail())
    }

    fun stopRecording() {
        val active = recording ?: return
        finalizing = true
        active.stop()
    }

    fun zoomIn() {
        if (!bound) return
        val base = max(targetZoom, currentZoom)
        val step = when {
            base < 2f -> 0.1f
            base < 5f -> 0.5f
            else -> 1f
        }
        setZoom(base + step)
    }

    fun zoomOut() {
        if (!bound) return
        val step = when {
            targetZoom <= 2.05f -> 0.1f
            targetZoom <= 5.05f -> 0.5f
            else -> 1f
        }
        setZoom(targetZoom - step)
    }

    fun setZoom(ratio: Float) {
        if (!bound) return
        val upper = if (maxZoom >= minZoom) maxZoom else ratio
        targetZoom = ratio.coerceIn(minZoom, upper)
        boundCamera?.cameraControl?.setZoomRatio(targetZoom)
    }

    fun setFlash(mode: FlashMode) {
        val camera = boundCamera
        if (!bound || camera == null) return
        if (!camera.cameraInfo.hasFlashUnit()) {
            requestedFlash = FlashMode.OFF
            flashSupported = false
            torchSupported = false
            publishControls(force = true)
            return
        }
        requestedFlash = when {
            mode == FlashMode.TORCH && !torchSupported -> FlashMode.OFF
            mode == FlashMode.ON || mode == FlashMode.AUTO || mode == FlashMode.TORCH || mode == FlashMode.OFF -> mode
            else -> FlashMode.OFF
        }
        applyFlash(camera)
    }

    fun exposureUp() = nudgeExposure(1)

    fun exposureDown() = nudgeExposure(-1)

    fun switchCamera() {
        if (recording != null || finalizing) {
            onStatus(PhoneStatus.SWITCH_WHILE_RECORDING, PhoneStatus.SWITCH_WHILE_RECORDING.message)
            return
        }
        val cameraProvider = provider ?: return
        val next = if (facing == CameraFacing.BACK) CameraFacing.FRONT else CameraFacing.BACK
        val selector = next.toSelector()
        if (!cameraProvider.hasLens(selector)) {
            onStatus(PhoneStatus.CAMERA_UNAVAILABLE, "Esa cámara no está disponible.")
            return
        }
        val previous = facing
        facing = next
        requestedFlash = FlashMode.OFF
        exposureIndex = 0
        zoomInitialized = false
        targetZoom = 1f
        try {
            bindNow()
            val ready = if (facing == CameraFacing.FRONT) "Cámara frontal lista." else "Cámara trasera lista."
            onStatus(PhoneStatus.READY, ready)
        } catch (error: Exception) {
            Log.e(TAG, "Cambio de cámara falló", error)
            facing = previous
            zoomInitialized = false
            try {
                bindNow()
                onStatus(PhoneStatus.CAMERA_UNAVAILABLE, "No se pudo cambiar de cámara.")
            } catch (nested: Exception) {
                Log.e(TAG, "No se pudo restaurar la cámara", nested)
                onStatus(PhoneStatus.CAMERA_UNAVAILABLE, PhoneStatus.CAMERA_UNAVAILABLE.message)
            }
        }
    }

    fun release() {
        recording?.stop()
        recording = null
        finalizing = false
        zoomLive?.removeObserver(zoomObserver)
        zoomLive = null
        runCatching { provider?.unbindAll() }
        cameraExecutor.shutdown()
        bound = false
    }

    private fun bindNow() {
        val cameraProvider = provider ?: return
        zoomLive?.removeObserver(zoomObserver)
        zoomLive = null
        boundCamera = null
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        capture.flashMode = requestedFlash.toCaptureMode()
        val recorder = Recorder.Builder()
            .setQualitySelector(
                QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)),
            )
            .build()
        val video = VideoCapture.withOutput(recorder)
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { it.setAnalyzer(cameraExecutor, ::analyze) }
        val selector = facing.toSelector()
        cameraProvider.unbindAll()
        val camera = try {
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                selector,
                previewUseCase,
                capture,
                video,
                analysis,
            )
        } catch (error: Exception) {
            Log.w(TAG, "Bind completo falló, reintento sin Preview UI: ${error.message}")
            cameraProvider.bindToLifecycle(lifecycleOwner, selector, capture, video, analysis)
        }
        imageCapture = capture
        videoCapture = video
        bound = true
        surfaceProvider?.let { previewUseCase.setSurfaceProvider(it) }
        configure(camera)
    }

    private fun configure(camera: Camera) {
        boundCamera = camera
        mirrorFront = facing == CameraFacing.FRONT
        flashSupported = camera.cameraInfo.hasFlashUnit()
        if (!flashSupported) {
            torchSupported = false
            requestedFlash = FlashMode.OFF
        } else if (!torchSupported && requestedFlash == FlashMode.TORCH) {
            requestedFlash = FlashMode.OFF
        } else {
            torchSupported = true
        }
        readExposure(camera)
        canSwitchCamera = provider?.let { current ->
            current.hasLens(CameraSelector.DEFAULT_BACK_CAMERA) &&
                current.hasLens(CameraSelector.DEFAULT_FRONT_CAMERA)
        } == true
        zoomLive = camera.cameraInfo.zoomState
        zoomLive?.observe(lifecycleOwner, zoomObserver)
        if (flashSupported) {
            applyFlash(camera)
        } else {
            imageCapture?.flashMode = ImageCapture.FLASH_MODE_OFF
            publishControls(force = true)
        }
    }

    private fun applyFlash(camera: Camera) {
        val mode = requestedFlash
        imageCapture?.flashMode = mode.toCaptureMode()
        val future = camera.cameraControl.enableTorch(mode == FlashMode.TORCH)
        future.addListener(
            {
                val ok = runCatching { future.get() }.isSuccess
                if (mode == FlashMode.TORCH && !ok) {
                    torchSupported = false
                    if (requestedFlash == FlashMode.TORCH) {
                        requestedFlash = FlashMode.OFF
                        imageCapture?.flashMode = ImageCapture.FLASH_MODE_OFF
                    }
                }
                publishControls(force = true)
            },
            mainExecutor,
        )
        publishControls(force = true)
    }

    private fun readExposure(camera: Camera) {
        val state = camera.cameraInfo.exposureState
        if (!state.isExposureCompensationSupported) {
            exposureMin = 0
            exposureMax = 0
            exposureStepEv = 0f
            exposureIndex = 0
            return
        }
        exposureMin = state.exposureCompensationRange.lower
        exposureMax = state.exposureCompensationRange.upper
        exposureStepEv = state.exposureCompensationStep.toFloat()
        if (exposureIndex !in exposureMin..exposureMax) {
            exposureIndex = 0.coerceIn(exposureMin, exposureMax)
        }
        if (exposureIndex != 0) {
            camera.cameraControl.setExposureCompensationIndex(exposureIndex)
        }
    }

    private fun nudgeExposure(direction: Int) {
        val camera = boundCamera ?: return
        val state = camera.cameraInfo.exposureState
        if (!state.isExposureCompensationSupported) return
        val range = state.exposureCompensationRange
        val span = (range.upper - range.lower).coerceAtLeast(1)
        val step = (span / 12).coerceAtLeast(1)
        val next = (exposureIndex + direction * step).coerceIn(range.lower, range.upper)
        if (next == exposureIndex) return
        exposureIndex = next
        camera.cameraControl.setExposureCompensationIndex(next)
        publishControls(force = true)
    }

    private fun onVideoEvent(event: VideoRecordEvent) {
        when (event) {
            is VideoRecordEvent.Finalize -> {
                finalizing = false
                onRecording(false)
                recording = null
                if (event.hasError()) {
                    onStatus(
                        PhoneStatus.RECORD_ERROR,
                        "${PhoneStatus.RECORD_ERROR.message} ${event.cause?.message ?: ""}".trim(),
                    )
                } else {
                    onStatus(PhoneStatus.VIDEO_SAVED, PhoneStatus.VIDEO_SAVED.message)
                }
            }
            is VideoRecordEvent.Start -> {
                onRecording(true)
                onStatus(PhoneStatus.RECORDING, recordingDetail())
            }
        }
    }

    private fun recordingDetail(): String = "Grabando · ${snapshot().shortLabel()}"

    private fun analyze(image: ImageProxy) {
        if (!hub.hasConsumers()) {
            image.close()
            return
        }
        val now = System.currentTimeMillis()
        val previous = lastFrameMs.get()
        if (now - previous < Protocol.PREVIEW_MIN_INTERVAL_MS) {
            image.close()
            return
        }
        lastFrameMs.set(now)
        try {
            val jpeg = image.toJpeg(Protocol.PREVIEW_MAX_EDGE, Protocol.JPEG_QUALITY, mirrorFront)
            if (jpeg != null) hub.update(jpeg)
        } catch (error: Exception) {
            Log.d(TAG, "Frame omitido: ${error.message}")
        } finally {
            image.close()
        }
    }

    private fun snapshot(): CameraControls = CameraControls(
        facing = facing,
        zoomRatio = currentZoom,
        minZoom = minZoom,
        maxZoom = maxZoom,
        flashMode = if (flashSupported || torchSupported) requestedFlash else FlashMode.OFF,
        flashSupported = flashSupported,
        torchSupported = torchSupported && flashSupported,
        exposureIndex = exposureIndex,
        exposureMin = exposureMin,
        exposureMax = exposureMax,
        exposureStep = exposureStepEv,
        canSwitchCamera = canSwitchCamera,
    )

    private fun publishControls(force: Boolean = false) {
        val controls = snapshot()
        val key = listOf(
            controls.facing.name,
            formatZoom(controls.zoomRatio),
            formatZoom(controls.minZoom),
            formatZoom(controls.maxZoom),
            controls.flashMode.name,
            controls.flashSupported,
            controls.torchSupported,
            controls.exposureIndex,
            controls.exposureMin,
            controls.exposureMax,
            controls.canSwitchCamera,
        ).joinToString("|")
        if (!force && key == lastPublishedKey) return
        lastPublishedKey = key
        onControls(controls)
    }

    private fun fileName(ext: String): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "CW_$stamp.$ext"
    }

    companion object {
        private const val TAG = "PhoneCamera"
    }
}

private fun CameraFacing.toSelector(): CameraSelector = when (this) {
    CameraFacing.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
    CameraFacing.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
}

private fun FlashMode.toCaptureMode(): Int = when (this) {
    FlashMode.ON -> ImageCapture.FLASH_MODE_ON
    FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
    FlashMode.OFF, FlashMode.TORCH -> ImageCapture.FLASH_MODE_OFF
}

private fun ProcessCameraProvider.hasLens(selector: CameraSelector): Boolean =
    runCatching { hasCamera(selector) }.getOrDefault(false)

private fun ImageProxy.toJpeg(maxEdge: Int, quality: Int, mirror: Boolean): ByteArray? {
    val plane = planes.firstOrNull() ?: return null
    val buffer = plane.buffer
    buffer.rewind()
    val pixelStride = plane.pixelStride
    val rowStride = plane.rowStride
    val rowPadding = rowStride - pixelStride * width
    val src = if (rowPadding == 0) {
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { it.copyPixelsFromBuffer(buffer) }
    } else {
        val paddedWidth = width + rowPadding / pixelStride
        val padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
        padded.copyPixelsFromBuffer(buffer)
        val cropped = Bitmap.createBitmap(padded, 0, 0, width, height)
        padded.recycle()
        cropped
    }
    val rotation = imageInfo.rotationDegrees
    val longest = max(src.width, src.height).toFloat()
    val scale = if (longest > maxEdge) maxEdge / longest else 1f
    val matrix = Matrix().apply {
        if (rotation != 0) postRotate(rotation.toFloat())
        if (mirror) postScale(-1f, 1f)
        if (scale < 1f) postScale(scale, scale)
    }
    val changed = rotation != 0 || mirror || scale < 1f
    val out = if (!changed) src else Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    return try {
        val bytes = ByteArrayOutputStream()
        out.compress(Bitmap.CompressFormat.JPEG, quality, bytes)
        bytes.toByteArray()
    } finally {
        if (out !== src) out.recycle()
        src.recycle()
    }
}

private suspend fun <T> com.google.common.util.concurrent.ListenableFuture<T>.await(executor: Executor): T =
    suspendCoroutine { cont ->
        addListener(
            {
                try {
                    cont.resume(get())
                } catch (error: Exception) {
                    cont.resumeWithException(error)
                }
            },
            executor,
        )
    }
