package com.george.camarawatch.mobile.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
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
import com.george.camarawatch.mobile.preview.PreviewHub
import com.george.camarawatch.shared.PhoneStatus
import com.george.camarawatch.shared.Protocol
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.max

class RearCameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val hub: PreviewHub,
    private val onStatus: (PhoneStatus, String) -> Unit,
    private val onRecording: (Boolean) -> Unit,
) {
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private val lastFrameMs = AtomicLong(0L)
    private var bound = false

    val previewUseCase: Preview = Preview.Builder().build()

    suspend fun start() {
        val provider = ProcessCameraProvider.getInstance(context).await()
        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        val recorder = Recorder.Builder()
            .setQualitySelector(
                QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)),
            )
            .build()
        val videoCapture = VideoCapture.withOutput(recorder)
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { it.setAnalyzer(cameraExecutor, ::analyze) }

        provider.unbindAll()
        val selector = CameraSelector.DEFAULT_BACK_CAMERA
        try {
            provider.bindToLifecycle(
                lifecycleOwner,
                selector,
                previewUseCase,
                imageCapture,
                videoCapture,
                analysis,
            )
        } catch (error: Exception) {
            Log.w(TAG, "Bind completo falló, reintento sin Preview UI: ${error.message}")
            provider.bindToLifecycle(lifecycleOwner, selector, imageCapture, videoCapture, analysis)
        }
        this.imageCapture = imageCapture
        this.videoCapture = videoCapture
        bound = true
        onStatus(PhoneStatus.WAITING_WATCH, PhoneStatus.WAITING_WATCH.message)
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
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    onStatus(PhoneStatus.PHOTO_SAVED, PhoneStatus.PHOTO_SAVED.message)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Foto fallida", exception)
                    onStatus(PhoneStatus.PHOTO_ERROR, "${PhoneStatus.PHOTO_ERROR.message} ${exception.message ?: ""}".trim())
                }
            },
        )
    }

    fun startRecording() {
        if (recording != null) return
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
            pending.withAudioEnabled().start(ContextCompat.getMainExecutor(context), ::onVideoEvent)
        } catch (error: SecurityException) {
            pending.start(ContextCompat.getMainExecutor(context), ::onVideoEvent)
        }
        onRecording(true)
        onStatus(PhoneStatus.RECORDING, PhoneStatus.RECORDING.message)
    }

    fun stopRecording() {
        recording?.stop()
        recording = null
    }

    fun release() {
        recording?.stop()
        recording = null
        cameraExecutor.shutdown()
        bound = false
    }

    private fun onVideoEvent(event: VideoRecordEvent) {
        when (event) {
            is VideoRecordEvent.Finalize -> {
                onRecording(false)
                recording = null
                if (event.hasError()) {
                    onStatus(PhoneStatus.RECORD_ERROR, "${PhoneStatus.RECORD_ERROR.message} ${event.cause?.message ?: ""}".trim())
                } else {
                    onStatus(PhoneStatus.VIDEO_SAVED, PhoneStatus.VIDEO_SAVED.message)
                }
            }
            is VideoRecordEvent.Start -> {
                onRecording(true)
                onStatus(PhoneStatus.RECORDING, PhoneStatus.RECORDING.message)
            }
        }
    }

    private fun analyze(image: ImageProxy) {
        val now = System.currentTimeMillis()
        val previous = lastFrameMs.get()
        if (now - previous < Protocol.PREVIEW_MIN_INTERVAL_MS) {
            image.close()
            return
        }
        lastFrameMs.set(now)
        try {
            val jpeg = image.toJpeg(Protocol.PREVIEW_MAX_EDGE, Protocol.JPEG_QUALITY)
            if (jpeg != null) hub.update(jpeg)
        } catch (error: Exception) {
            Log.d(TAG, "Frame omitido: ${error.message}")
        } finally {
            image.close()
        }
    }

    private fun fileName(ext: String): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "CW_$stamp.$ext"
    }

    companion object {
        private const val TAG = "RearCamera"
    }
}

private fun ImageProxy.toJpeg(maxEdge: Int, quality: Int): ByteArray? {
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
        if (scale < 1f) postScale(scale, scale)
    }
    val out = if (rotation == 0 && scale >= 1f) {
        src
    } else {
        Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }
    return try {
        val bytes = ByteArrayOutputStream()
        out.compress(Bitmap.CompressFormat.JPEG, quality, bytes)
        bytes.toByteArray()
    } finally {
        if (out !== src) out.recycle()
        src.recycle()
    }
}

private suspend fun <T> com.google.common.util.concurrent.ListenableFuture<T>.await(): T =
    suspendCoroutine { cont ->
        addListener(
            {
                try {
                    cont.resume(get())
                } catch (error: Exception) {
                    cont.resumeWithException(error)
                }
            },
            MoreExecutorsDirect,
        )
    }

private object MoreExecutorsDirect : java.util.concurrent.Executor {
    override fun execute(command: Runnable) = command.run()
}
