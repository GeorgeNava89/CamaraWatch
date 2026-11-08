package com.george.camarawatch.wear.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.george.camarawatch.shared.CameraCommand
import com.george.camarawatch.shared.CameraControls
import com.george.camarawatch.shared.CameraFacing
import com.george.camarawatch.shared.FlashMode
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

private val RecordRed = Color(0xFFFF3B30)
private val Scrim = Color(0xB3000000)

private enum class CaptureMode { PHOTO, VIDEO }

@Composable
fun WatchScreen(
    status: StateFlow<String>,
    recording: StateFlow<Boolean>,
    preview: StateFlow<ImageBitmap?>,
    usingWifi: StateFlow<Boolean>,
    controls: StateFlow<CameraControls>,
    ambient: StateFlow<Boolean>,
    timerSeconds: StateFlow<Int>,
    countdown: StateFlow<Int>,
    onCommand: (CameraCommand) -> Unit,
    onShutter: (video: Boolean) -> Unit,
    onTimer: (Int) -> Unit,
    onCancelCountdown: () -> Unit,
) {
    val statusText by status.collectAsState()
    val isRecording by recording.collectAsState()
    val frame by preview.collectAsState()
    val wifi by usingWifi.collectAsState()
    val camera by controls.collectAsState()
    val isAmbient by ambient.collectAsState()
    val timer by timerSeconds.collectAsState()
    val remaining by countdown.collectAsState()
    var mode by remember { mutableStateOf(CaptureMode.PHOTO) }
    var optionsOpen by remember { mutableStateOf(false) }

    LaunchedEffect(isRecording) {
        if (isRecording) mode = CaptureMode.VIDEO
    }

    if (isAmbient) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Cámara Watch", color = Color.White, style = MaterialTheme.typography.title3)
                Text("En reposo", color = Color.White, style = MaterialTheme.typography.caption2)
            }
        }
        return
    }

    val counting = remaining > 0
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val side = if (maxWidth < maxHeight) maxWidth else maxHeight
        val safe = side * 0.16f
        val edge = side * 0.075f
        if (frame != null) {
            Image(
                bitmap = frame!!,
                contentDescription = "Vista previa",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        if (!optionsOpen && !counting) {
            ModeGestures(
                videoMode = mode == CaptureMode.VIDEO,
                recording = isRecording,
                onVideo = { mode = CaptureMode.VIDEO },
                onPhoto = { mode = CaptureMode.PHOTO },
                onOpenOptions = { optionsOpen = true },
            )
        }
        CaptureOverlay(
            safe = safe,
            statusText = if (counting) "" else statusText,
            wifi = wifi && !counting,
            controls = camera,
            mode = mode,
            recording = isRecording,
            countdown = remaining,
            onShutter = {
                if (counting) onCancelCountdown() else onShutter(mode == CaptureMode.VIDEO)
            },
        )
        if (!optionsOpen && !counting) {
            if (mode == CaptureMode.PHOTO) {
                Hint(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = edge)
                        .semantics { contentDescription = "Desliza a la derecha para video" },
                ) {
                    VideoGlyph()
                    Chevron(ChevronDirection.RIGHT)
                }
            } else if (!isRecording) {
                Hint(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = edge)
                        .semantics { contentDescription = "Desliza a la izquierda para foto" },
                ) {
                    Chevron(ChevronDirection.LEFT)
                    CameraGlyph()
                }
            }
        }
        if (optionsOpen) {
            OptionsPanel(
                controls = camera,
                recording = isRecording,
                timerSeconds = timer,
                onCommand = onCommand,
                onTimer = onTimer,
                onClose = { optionsOpen = false },
            )
        }
    }
}

@Composable
private fun ModeGestures(
    videoMode: Boolean,
    recording: Boolean,
    onVideo: () -> Unit,
    onPhoto: () -> Unit,
    onOpenOptions: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(videoMode, recording) {
                val threshold = 64.dp.toPx()
                var totalX = 0f
                var totalY = 0f
                detectDragGestures(
                    onDragStart = {
                        totalX = 0f
                        totalY = 0f
                    },
                    onDrag = { change, drag ->
                        totalX += drag.x
                        totalY += drag.y
                        if (abs(totalX) > threshold || abs(totalY) > threshold) change.consume()
                    },
                    onDragEnd = {
                        val absX = abs(totalX)
                        val absY = abs(totalY)
                        when {
                            absY > absX * 1.15f && absY > threshold && totalY < 0f -> onOpenOptions()
                            absX > absY * 1.15f && absX > threshold && totalX > 0f && !videoMode -> onVideo()
                            absX > absY * 1.15f && absX > threshold && totalX < 0f && videoMode && !recording -> onPhoto()
                        }
                    },
                )
            },
    )
}

@Composable
private fun CaptureOverlay(
    safe: Dp,
    statusText: String,
    wifi: Boolean,
    controls: CameraControls,
    mode: CaptureMode,
    recording: Boolean,
    countdown: Int,
    onShutter: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(safe),
    ) {
        Column(
            modifier = Modifier.align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (recording) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(RecordRed),
                    )
                    Spacer(Modifier.size(4.dp))
                    Text("REC", color = RecordRed, style = MaterialTheme.typography.caption2)
                }
            }
            Text(
                text = controls.zoomLabel(),
                color = Color.White,
                style = MaterialTheme.typography.caption1,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Scrim)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
            if (wifi) {
                Text(
                    text = "Wi‑Fi",
                    color = Color.White,
                    style = MaterialTheme.typography.caption2,
                )
            }
            if (countdown <= 0 && statusText.isNotBlank()) {
                Text(
                    text = statusText,
                    color = Color.White,
                    style = MaterialTheme.typography.caption2,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .widthIn(max = 150.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Scrim)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        if (countdown > 0) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = countdown.toString(),
                    color = Color.White,
                    style = MaterialTheme.typography.display2,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Scrim)
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
                Text(
                    text = "Toca para cancelar",
                    color = Color.White,
                    style = MaterialTheme.typography.caption2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clip(CircleShape)
                        .background(Scrim)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        Column(
            modifier = Modifier.align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (countdown <= 0) {
                Chevron(ChevronDirection.UP)
                Spacer(Modifier.size(6.dp))
            }
            Shutter(
                video = mode == CaptureMode.VIDEO,
                recording = recording,
                onClick = onShutter,
            )
        }
    }
}

@Composable
private fun Shutter(video: Boolean, recording: Boolean, onClick: () -> Unit) {
    val description = when {
        recording -> "Parar"
        video -> "Grabar"
        else -> "Foto"
    }
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .border(3.dp, Color.White, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        when {
            recording -> Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White),
            )
            video -> Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(RecordRed),
            )
            else -> Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

@Composable
private fun OptionsPanel(
    controls: CameraControls,
    recording: Boolean,
    timerSeconds: Int,
    onCommand: (CameraCommand) -> Unit,
    onTimer: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val scroll = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xE6000000))
                .clickable(onClick = onClose),
        )
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.72f)
                .fillMaxHeight(0.72f)
                .verticalScroll(scroll)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            var dy = 0f
                            detectVerticalDragGestures(
                                onDragStart = { dy = 0f },
                                onVerticalDrag = { _, amount -> dy += amount },
                                onDragEnd = { if (dy > 36.dp.toPx()) onClose() },
                            )
                        },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Chevron(ChevronDirection.DOWN)
                    Spacer(Modifier.size(4.dp))
                    Text("Opciones", color = Color.White, style = MaterialTheme.typography.title3)
                }
                if (controls.flashControlVisible) {
                    SectionLabel("Flash")
                    val modes = buildList {
                        add(FlashMode.OFF)
                        if (controls.flashSupported) {
                            add(FlashMode.ON)
                            add(FlashMode.AUTO)
                        }
                        if (controls.torchSupported) add(FlashMode.TORCH)
                    }
                    modes.chunked(2).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            row.forEach { mode ->
                                Choice(
                                    text = mode.chipLabel(),
                                    selected = controls.flashMode == mode,
                                    onClick = { onCommand(CameraCommand.SetFlash(mode)) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                SectionLabel("Temporizador")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(0 to "Apagado", 3 to "3 s", 10 to "10 s").forEach { (seconds, label) ->
                        Choice(
                            text = label,
                            selected = timerSeconds == seconds,
                            onClick = { onTimer(seconds) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (controls.zoomSupported) {
                    SectionLabel("Zoom")
                    Stepper(
                        value = controls.zoomLabel(),
                        minusEnabled = controls.zoomRatio > controls.minZoom + 0.03f,
                        plusEnabled = controls.zoomRatio < controls.maxZoom - 0.03f,
                        onMinus = { onCommand(CameraCommand.ZoomOut) },
                        onPlus = { onCommand(CameraCommand.ZoomIn) },
                    )
                }
                if (controls.exposureSupported) {
                    SectionLabel("Exposición")
                    Stepper(
                        value = controls.exposureValue(),
                        minusLabel = "Oscuro",
                        plusLabel = "Claro",
                        minusEnabled = controls.exposureIndex > controls.exposureMin,
                        plusEnabled = controls.exposureIndex < controls.exposureMax,
                        onMinus = { onCommand(CameraCommand.ExposureDown) },
                        onPlus = { onCommand(CameraCommand.ExposureUp) },
                    )
                }
                if (controls.canSwitchCamera) {
                    val target = if (controls.facing == CameraFacing.BACK) "frontal" else "trasera"
                    Choice(
                        text = if (recording) "No disponible al grabar" else "Cambiar a $target",
                        selected = false,
                        enabled = !recording,
                        onClick = { onCommand(CameraCommand.SwitchCamera) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                val nothing = !controls.flashControlVisible && !controls.zoomSupported &&
                    !controls.exposureSupported && !controls.canSwitchCamera
                if (nothing) {
                    Text(
                        text = "Esperando la cámara…",
                        color = Color.White,
                        style = MaterialTheme.typography.caption2,
                        textAlign = TextAlign.Center,
                    )
                }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = Color.White,
        style = MaterialTheme.typography.caption2,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun Choice(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Text(
        text = text,
        color = when {
            !enabled -> Color(0xFF8A8A8A)
            selected -> Color.Black
            else -> Color.White
        },
        style = MaterialTheme.typography.caption2,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .defaultMinSize(minHeight = 40.dp)
            .clip(CircleShape)
            .background(if (selected && enabled) Color.White else Color(0xF21A1A1A))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    )
}

@Composable
private fun Stepper(
    value: String,
    minusEnabled: Boolean,
    plusEnabled: Boolean,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    minusLabel: String = "−",
    plusLabel: String = "+",
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Choice(
            text = minusLabel,
            selected = false,
            enabled = minusEnabled,
            onClick = onMinus,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            color = Color.White,
            style = MaterialTheme.typography.title3,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 44.dp),
        )
        Choice(
            text = plusLabel,
            selected = false,
            enabled = plusEnabled,
            onClick = onPlus,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Hint(modifier: Modifier, content: @Composable () -> Unit) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0x80303030))
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        content()
    }
}

private enum class ChevronDirection { LEFT, RIGHT, UP, DOWN }

@Composable
private fun Chevron(direction: ChevronDirection) {
    Canvas(modifier = Modifier.size(16.dp)) {
        val stroke = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
        val path = Path()
        val w = size.width
        val h = size.height
        when (direction) {
            ChevronDirection.RIGHT -> {
                path.moveTo(w * 0.32f, h * 0.18f)
                path.lineTo(w * 0.72f, h * 0.5f)
                path.lineTo(w * 0.32f, h * 0.82f)
            }
            ChevronDirection.LEFT -> {
                path.moveTo(w * 0.68f, h * 0.18f)
                path.lineTo(w * 0.28f, h * 0.5f)
                path.lineTo(w * 0.68f, h * 0.82f)
            }
            ChevronDirection.UP -> {
                path.moveTo(w * 0.18f, h * 0.64f)
                path.lineTo(w * 0.5f, h * 0.28f)
                path.lineTo(w * 0.82f, h * 0.64f)
            }
            ChevronDirection.DOWN -> {
                path.moveTo(w * 0.18f, h * 0.36f)
                path.lineTo(w * 0.5f, h * 0.72f)
                path.lineTo(w * 0.82f, h * 0.36f)
            }
        }
        drawPath(path, Color.White, style = stroke)
    }
}

@Composable
private fun VideoGlyph() {
    Canvas(modifier = Modifier.size(14.dp)) {
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(0f, size.height * 0.22f),
            size = Size(size.width * 0.58f, size.height * 0.56f),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
        )
        val path = Path().apply {
            moveTo(size.width * 0.66f, size.height * 0.3f)
            lineTo(size.width * 0.96f, size.height * 0.16f)
            lineTo(size.width * 0.96f, size.height * 0.84f)
            lineTo(size.width * 0.66f, size.height * 0.7f)
            close()
        }
        drawPath(path, Color.White)
    }
}

@Composable
private fun CameraGlyph() {
    Canvas(modifier = Modifier.size(14.dp)) {
        drawCircle(
            color = Color.White,
            radius = size.minDimension * 0.38f,
            style = Stroke(width = 1.6.dp.toPx()),
        )
        drawCircle(color = Color.White, radius = size.minDimension * 0.16f)
    }
}
