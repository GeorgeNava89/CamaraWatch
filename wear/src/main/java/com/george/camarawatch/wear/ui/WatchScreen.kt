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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
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
import com.george.camarawatch.shared.formatZoom
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin

private val RecordRed = Color(0xFFFF3B30)
private val Scrim = Color(0xB3000000)
private val IconLight = Color(0xFFF4F4F4)
private val FlashOn = Color(0xFFFFF176)
private val FlashAuto = Color(0xFF64B5FF)
private val FlashTorch = Color(0xFFFFC14D)

private enum class CaptureMode { PHOTO, VIDEO }

@Composable
fun WatchScreen(
    status: StateFlow<String>,
    recording: StateFlow<Boolean>,
    preview: StateFlow<ImageBitmap?>,
    controls: StateFlow<CameraControls>,
    ambient: StateFlow<Boolean>,
    timerSeconds: StateFlow<Int>,
    countdown: StateFlow<Int>,
    onCommand: (CameraCommand) -> Unit,
    onZoom: (Float) -> Unit,
    onShutter: (video: Boolean) -> Unit,
    onTimer: (Int) -> Unit,
    onCancelCountdown: () -> Unit,
) {
    val statusText by status.collectAsState()
    val isRecording by recording.collectAsState()
    val frame by preview.collectAsState()
    val camera by controls.collectAsState()
    val isAmbient by ambient.collectAsState()
    val timer by timerSeconds.collectAsState()
    val remaining by countdown.collectAsState()
    var mode by remember { mutableStateOf(CaptureMode.PHOTO) }
    var optionsOpen by remember { mutableStateOf(false) }
    var zoomDrag by remember { mutableStateOf<Float?>(null) }

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
        val problem = if (counting) null else seriousNotice(statusText)
        if (frame != null) {
            Image(
                bitmap = frame!!,
                contentDescription = "Vista previa",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        if (!optionsOpen) {
            ZoomArcLayer(
                controls = camera,
                zoomEnabled = camera.zoomSupported && !counting,
                allowPanel = !counting,
                dragging = zoomDrag,
                onDragging = { zoomDrag = it },
                onZoom = onZoom,
                onOpenOptions = { optionsOpen = true },
            )
        }
        CaptureOverlay(
            safe = safe,
            problem = problem,
            zoomReadout = zoomDrag?.let { formatZoom(it) + "x" },
            mode = mode,
            recording = isRecording,
            countdown = remaining,
            onShutter = {
                if (counting) onCancelCountdown() else onShutter(mode == CaptureMode.VIDEO)
            },
        )
        if (!optionsOpen && !counting) {
            Column(
                modifier = Modifier.align(Alignment.CenterStart),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                if (camera.flashControlVisible) {
                    val nextFlash = camera.flashMode.next(camera.flashSupported, camera.torchSupported)
                    IconButton(
                        description = "Flash ${camera.flashMode.chipLabel()}",
                        onClick = { if (nextFlash != null) onCommand(CameraCommand.SetFlash(nextFlash)) },
                    ) {
                        FlashGlyph(camera.flashMode)
                        if (camera.flashMode == FlashMode.AUTO) {
                            Text("A", color = FlashAuto, style = MaterialTheme.typography.caption2)
                        }
                    }
                }
                IconButton(
                    description = when (timer) {
                        3 -> "Temporizador 3 segundos"
                        10 -> "Temporizador 10 segundos"
                        else -> "Temporizador apagado"
                    },
                    onClick = {
                        onTimer(
                            when (timer) {
                                3 -> 10
                                10 -> 0
                                else -> 3
                            },
                        )
                    },
                ) {
                    if (timer == 3 || timer == 10) {
                        Text(
                            timer.toString(),
                            color = IconLight,
                            style = MaterialTheme.typography.title3,
                        )
                    } else {
                        TimerGlyph()
                    }
                }
            }
            IconButton(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(y = (-4).dp),
                alignEnd = true,
                description = when {
                    isRecording -> "Para primero para cambiar de modo"
                    mode == CaptureMode.PHOTO -> "Cambiar a video"
                    else -> "Cambiar a foto"
                },
                enabled = !isRecording,
                onClick = {
                    if (!isRecording) {
                        mode = if (mode == CaptureMode.PHOTO) CaptureMode.VIDEO else CaptureMode.PHOTO
                    }
                },
            ) {
                if (mode == CaptureMode.PHOTO) VideoGlyph() else CameraGlyph()
            }
            if (camera.canSwitchCamera && !isRecording) {
                IconButton(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(y = 40.dp),
                    alignEnd = true,
                    description = if (camera.facing == CameraFacing.BACK) {
                        "Cambiar a frontal"
                    } else {
                        "Cambiar a trasera"
                    },
                    onClick = { onCommand(CameraCommand.SwitchCamera) },
                ) {
                    SwitchCameraGlyph()
                }
            }
        }
        if (!optionsOpen && !counting) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 5.dp),
            ) {
                Chevron(ChevronDirection.UP)
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
private fun ZoomArcLayer(
    controls: CameraControls,
    zoomEnabled: Boolean,
    allowPanel: Boolean,
    dragging: Float?,
    onDragging: (Float?) -> Unit,
    onZoom: (Float) -> Unit,
    onOpenOptions: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val widthPx = constraints.maxWidth.toFloat()
    val heightPx = constraints.maxHeight.toFloat()
    val labelGeom = arcGeom(widthPx, heightPx, 0f)
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (!zoomEnabled && dragging == null) return@Canvas
        if (!controls.zoomSupported) return@Canvas
        val geom = arcGeom(size.width, size.height, 0f)
        val arcRect = arcRect(geom)
        val stroke = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
        drawArc(
            color = Color.Black.copy(alpha = 0.45f),
            startAngle = ARC_START,
            sweepAngle = ARC_SWEEP,
            useCenter = false,
            topLeft = arcRect.topLeft,
            size = arcRect.size,
            style = Stroke(width = 4.5.dp.toPx(), cap = StrokeCap.Round),
        )
        drawArc(
            color = Color.White.copy(alpha = 0.42f),
            startAngle = ARC_START,
            sweepAngle = ARC_SWEEP,
            useCenter = false,
            topLeft = arcRect.topLeft,
            size = arcRect.size,
            style = stroke,
        )
        val shown = dragging ?: controls.zoomRatio
        val t = zoomFraction(controls.minZoom, controls.maxZoom, shown)
        if (t > 0.02f) {
            drawArc(
                color = Color.White.copy(alpha = 0.92f),
                startAngle = ARC_START,
                sweepAngle = ARC_SWEEP * t,
                useCenter = false,
                topLeft = arcRect.topLeft,
                size = arcRect.size,
                style = stroke,
            )
        }
        val thumb = pointOnArc(geom, t)
        drawCircle(Color.Black.copy(alpha = 0.55f), radius = 5.5.dp.toPx(), center = thumb)
        drawCircle(Color.White, radius = 3.4.dp.toPx(), center = thumb)
    }
    if (controls.zoomSupported) {
        ArcEndMark(zoomMark(controls.minZoom), inwardLabel(labelGeom, 0f))
        ArcEndMark(zoomMark(controls.maxZoom), inwardLabel(labelGeom, 1f))
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(zoomEnabled, allowPanel, controls.minZoom, controls.maxZoom) {
                val threshold = 56.dp.toPx()
                val tolerance = 28.dp.toPx()
                var totalX = 0f
                var totalY = 0f
                var zooming = false
                var latest = Float.NaN
                var lastSent = Float.NaN
                fun publish(ratio: Float, force: Boolean) {
                    latest = ratio
                    if (!force && !lastSent.isNaN() && abs(ratio - lastSent) < 0.05f) return
                    lastSent = ratio
                    onZoom(ratio)
                }
                detectDragGestures(
                    onDragStart = { start ->
                        totalX = 0f
                        totalY = 0f
                        latest = Float.NaN
                        val geom = arcGeom(size.width.toFloat(), size.height.toFloat(), tolerance)
                        val fraction = fractionOnArc(geom, start)
                        zooming = zoomEnabled && fraction != null
                        if (zooming && fraction != null) {
                            val ratio = ratioForFraction(controls.minZoom, controls.maxZoom, fraction)
                            onDragging(ratio)
                            publish(ratio, force = true)
                        }
                    },
                    onDrag = { change, drag ->
                        totalX += drag.x
                        totalY += drag.y
                        if (zooming) {
                            val geom = arcGeom(size.width.toFloat(), size.height.toFloat(), tolerance)
                            val fraction = fractionOnArc(geom, change.position, loose = true)
                            if (fraction != null) {
                                val ratio = ratioForFraction(controls.minZoom, controls.maxZoom, fraction)
                                onDragging(ratio)
                                publish(ratio, force = false)
                            }
                            change.consume()
                        }
                    },
                    onDragEnd = {
                        if (zooming && !latest.isNaN()) {
                            publish(latest, force = true)
                            onDragging(null)
                        } else if (
                            allowPanel &&
                            totalY < -threshold &&
                            abs(totalY) > abs(totalX) * 1.15f
                        ) {
                            onOpenOptions()
                        }
                        zooming = false
                    },
                    onDragCancel = {
                        onDragging(null)
                        zooming = false
                    },
                )
            },
    )
    }
}

@Composable
private fun ArcEndMark(text: String, at: Offset) {
    Text(
        text = text,
        color = Color.White,
        style = MaterialTheme.typography.caption2,
        modifier = Modifier.offset {
            IntOffset(
                (at.x - 12.dp.toPx()).toInt(),
                (at.y - 8.dp.toPx()).toInt(),
            )
        },
    )
}

@Composable
private fun CaptureOverlay(
    safe: Dp,
    problem: String?,
    zoomReadout: String?,
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
            if (zoomReadout != null) {
                Text(
                    text = zoomReadout,
                    color = IconLight,
                    style = MaterialTheme.typography.caption1,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Scrim)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            if (!problem.isNullOrBlank()) {
                Text(
                    text = problem,
                    color = IconLight,
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
                    color = IconLight,
                    style = MaterialTheme.typography.display2,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Scrim)
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
                Text(
                    text = "Toca para cancelar",
                    color = IconLight,
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
        Shutter(
            modifier = Modifier.align(Alignment.BottomCenter),
            video = mode == CaptureMode.VIDEO,
            recording = recording,
            onClick = onShutter,
        )
    }
}

@Composable
private fun Shutter(
    video: Boolean,
    recording: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = when {
        recording -> "Parar"
        video -> "Grabar"
        else -> "Foto"
    }
    Box(
        modifier = modifier
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
private fun IconButton(
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    alignEnd: Boolean = false,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        content()
    }
}

@Composable
private fun FlashGlyph(mode: FlashMode) {
    val color = when (mode) {
        FlashMode.OFF -> IconLight
        FlashMode.ON -> FlashOn
        FlashMode.AUTO -> FlashAuto
        FlashMode.TORCH -> FlashTorch
    }
    Canvas(modifier = Modifier.size(20.dp)) {
        val path = Path().apply {
            moveTo(size.width * 0.55f, size.height * 0.08f)
            lineTo(size.width * 0.22f, size.height * 0.54f)
            lineTo(size.width * 0.46f, size.height * 0.54f)
            lineTo(size.width * 0.40f, size.height * 0.92f)
            lineTo(size.width * 0.82f, size.height * 0.40f)
            lineTo(size.width * 0.56f, size.height * 0.40f)
            close()
        }
        if (mode == FlashMode.OFF) {
            drawPath(path, color, style = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round))
            drawLine(
                color = color,
                start = Offset(size.width * 0.16f, size.height * 0.84f),
                end = Offset(size.width * 0.84f, size.height * 0.16f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        } else {
            drawPath(path, color)
        }
    }
}

@Composable
private fun TimerGlyph() {
    Canvas(modifier = Modifier.size(16.dp)) {
        drawCircle(
            color = IconLight,
            radius = size.minDimension * 0.36f,
            style = Stroke(width = 1.6.dp.toPx()),
        )
        drawLine(
            color = IconLight,
            start = Offset(size.width * 0.5f, size.height * 0.5f),
            end = Offset(size.width * 0.5f, size.height * 0.28f),
            strokeWidth = 1.6.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = IconLight,
            start = Offset(size.width * 0.5f, size.height * 0.08f),
            end = Offset(size.width * 0.5f, size.height * 0.16f),
            strokeWidth = 1.6.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

private fun seriousNotice(text: String): String? {
    val normalized = text.lowercase()
    if (normalized.isBlank()) return null
    if (normalized.contains("wi") && normalized.contains("fi")) return null
    if (normalized.contains("vista previa")) return null
    if (normalized.contains("wear")) return null
    if (normalized.contains("channel")) return null
    return when {
        normalized.contains("sin conexión") ||
            normalized.contains("no está conectado") ||
            normalized.contains("sin nodo") ||
            normalized.contains("sin teléfono") -> "Sin teléfono"
        normalized.contains("falta permiso") -> "Falta permiso"
        normalized.contains("no se puede cambiar") -> "Para primero"
        normalized.contains("error") -> "Error"
        normalized.contains("no disponible") || normalized.contains("no se pudo") -> "Sin cámara"
        else -> null
    }
}

private data class ArcGeom(val center: Offset, val radius: Float, val tolerance: Float)

private const val ARC_START = 222f
private const val ARC_SWEEP = 96f

private fun arcGeom(width: Float, height: Float, tolerance: Float): ArcGeom {
    val side = if (width < height) width else height
    return ArcGeom(Offset(width / 2f, height / 2f), side * 0.44f, tolerance)
}

private data class ArcRect(val topLeft: Offset, val size: Size)

private fun arcRect(geom: ArcGeom) = ArcRect(
    topLeft = Offset(geom.center.x - geom.radius, geom.center.y - geom.radius),
    size = Size(geom.radius * 2f, geom.radius * 2f),
)

private fun arcDegrees(fraction: Float): Float = ARC_START + ARC_SWEEP * fraction.coerceIn(0f, 1f)

private fun fractionOnArc(geom: ArcGeom, point: Offset, loose: Boolean = false): Float? {
    val dx = point.x - geom.center.x
    val dy = point.y - geom.center.y
    val distance = hypot(dx, dy)
    val limit = if (loose) geom.tolerance * 1.6f else geom.tolerance
    if (limit > 0f && abs(distance - geom.radius) > limit) return null
    var degrees = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    if (degrees < 0f) degrees += 360f
    val end = ARC_START + ARC_SWEEP
    if (degrees < ARC_START - 6f || degrees > end + 6f) return null
    return ((degrees - ARC_START) / ARC_SWEEP).coerceIn(0f, 1f)
}

private fun pointOnArc(geom: ArcGeom, fraction: Float): Offset {
    val radians = Math.toRadians(arcDegrees(fraction).toDouble())
    return Offset(
        x = geom.center.x + (geom.radius * cos(radians)).toFloat(),
        y = geom.center.y + (geom.radius * sin(radians)).toFloat(),
    )
}

private fun inwardLabel(geom: ArcGeom, fraction: Float): Offset {
    val end = pointOnArc(geom, fraction)
    val dx = geom.center.x - end.x
    val dy = geom.center.y - end.y
    val length = hypot(dx, dy).coerceAtLeast(1f)
    val inset = geom.radius * 0.16f
    return Offset(end.x + dx / length * inset, end.y + dy / length * inset)
}

private fun zoomMark(ratio: Float): String {
    val nearest = ratio.toInt().toFloat()
    return if (abs(ratio - nearest) < 0.08f) "${nearest.toInt()}X" else String.format("%.1fX", ratio)
}

private fun zoomFraction(min: Float, max: Float, ratio: Float): Float {
    if (max <= min + 0.001f) return 0f
    val clamped = ratio.coerceIn(min, max)
    return if (min > 0f && max / min > 1.2f) {
        (ln(clamped / min) / ln(max / min)).toFloat().coerceIn(0f, 1f)
    } else {
        ((clamped - min) / (max - min)).coerceIn(0f, 1f)
    }
}

private fun ratioForFraction(min: Float, max: Float, fraction: Float): Float {
    val t = fraction.coerceIn(0f, 1f)
    if (max <= min) return min
    return if (min > 0f && max / min > 1.2f) {
        min * (max / min).toDouble().pow(t.toDouble()).toFloat()
    } else {
        min + (max - min) * t
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
                        color = IconLight,
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
            color = IconLight,
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

private enum class ChevronDirection { UP, DOWN }

@Composable
private fun Chevron(direction: ChevronDirection) {
    Canvas(modifier = Modifier.size(16.dp)) {
        val stroke = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
        val path = Path()
        val w = size.width
        val h = size.height
        when (direction) {
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
    Canvas(modifier = Modifier.size(22.dp)) {
        drawRoundRect(
            color = IconLight,
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
private fun SwitchCameraGlyph() {
    Canvas(modifier = Modifier.size(22.dp)) {
        val stroke = Stroke(width = 2.1.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = Path().apply {
            moveTo(size.width * 0.22f, size.height * 0.40f)
            quadraticBezierTo(size.width * 0.50f, size.height * 0.02f, size.width * 0.80f, size.height * 0.36f)
            moveTo(size.width * 0.62f, size.height * 0.22f)
            lineTo(size.width * 0.84f, size.height * 0.36f)
            lineTo(size.width * 0.66f, size.height * 0.52f)
            moveTo(size.width * 0.78f, size.height * 0.60f)
            quadraticBezierTo(size.width * 0.50f, size.height * 0.98f, size.width * 0.20f, size.height * 0.64f)
            moveTo(size.width * 0.38f, size.height * 0.78f)
            lineTo(size.width * 0.16f, size.height * 0.64f)
            lineTo(size.width * 0.34f, size.height * 0.48f)
        }
        drawPath(path, Color.White, style = stroke)
    }
}

@Composable
private fun CameraGlyph() {
    Canvas(modifier = Modifier.size(22.dp)) {
        drawRoundRect(
            color = IconLight,
            topLeft = Offset(size.width * 0.04f, size.height * 0.28f),
            size = Size(size.width * 0.92f, size.height * 0.62f),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
        )
        drawRoundRect(
            color = IconLight,
            topLeft = Offset(size.width * 0.18f, size.height * 0.08f),
            size = Size(size.width * 0.28f, size.height * 0.24f),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
        )
        drawCircle(
            color = Color(0xFF3A3A3A),
            radius = size.minDimension * 0.16f,
            center = Offset(size.width * 0.5f, size.height * 0.58f),
        )
        drawCircle(
            color = IconLight,
            radius = size.minDimension * 0.07f,
            center = Offset(size.width * 0.5f, size.height * 0.58f),
        )
    }
}
