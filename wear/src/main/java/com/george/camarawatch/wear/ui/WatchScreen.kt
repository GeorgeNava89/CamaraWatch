package com.george.camarawatch.wear.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactButton
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.george.camarawatch.shared.CameraCommand
import com.george.camarawatch.shared.CameraControls
import com.george.camarawatch.shared.CameraFacing
import kotlinx.coroutines.flow.StateFlow

private val RecordRed = Color(0xFFFF4D4D)
private val Scrim = Color(0x99000000)

@Composable
fun WatchScreen(
    status: StateFlow<String>,
    recording: StateFlow<Boolean>,
    preview: StateFlow<ImageBitmap?>,
    usingWifi: StateFlow<Boolean>,
    controls: StateFlow<CameraControls>,
    ambient: StateFlow<Boolean>,
    onCommand: (CameraCommand) -> Unit,
) {
    val statusText by status.collectAsState()
    val isRecording by recording.collectAsState()
    val frame by preview.collectAsState()
    val wifi by usingWifi.collectAsState()
    val camera by controls.collectAsState()
    val isAmbient by ambient.collectAsState()
    var page by remember { mutableIntStateOf(0) }

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

    if (page == 0) {
        CapturePage(
            statusText = statusText,
            isRecording = isRecording,
            frame = frame,
            wifi = wifi,
            controls = camera,
            page = page,
            onCommand = onCommand,
            onMore = { page = 1 },
        )
    } else {
        AdjustPage(
            frame = frame,
            isRecording = isRecording,
            controls = camera,
            onCommand = onCommand,
            onDone = { page = 0 },
        )
    }
}

@Composable
private fun CapturePage(
    statusText: String,
    isRecording: Boolean,
    frame: ImageBitmap?,
    wifi: Boolean,
    controls: CameraControls,
    page: Int,
    onCommand: (CameraCommand) -> Unit,
    onMore: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (frame != null) {
            Image(
                bitmap = frame,
                contentDescription = "Vista previa",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        ) {
            ScrimText(
                text = if (isRecording) "GRABANDO" else controls.shortLabel(),
                color = if (isRecording) RecordRed else Color.White,
            )
            if (isRecording || wifi) {
                val detail = listOfNotNull(
                    if (isRecording) controls.shortLabel() else null,
                    if (wifi) "Wi‑Fi" else null,
                ).joinToString(" · ")
                if (detail.isNotBlank()) {
                    ScrimText(text = detail, color = Color.White)
                }
            }
            if (frame == null && !isRecording) {
                Text(
                    text = "Sin vista previa",
                    color = Color.White,
                    style = MaterialTheme.typography.caption2,
                    textAlign = TextAlign.Center,
                )
            }
            if (controls.zoomSupported) {
                Stepper(
                    value = controls.zoomLabel(),
                    minusEnabled = controls.zoomRatio > controls.minZoom + 0.03f,
                    plusEnabled = controls.zoomRatio < controls.maxZoom - 0.03f,
                    onMinus = { onCommand(CameraCommand.ZoomOut) },
                    onPlus = { onCommand(CameraCommand.ZoomIn) },
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { onCommand(CameraCommand.TakePhoto) },
                    modifier = Modifier.size(56.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xE6121212)),
                ) {
                    Text("Foto", style = MaterialTheme.typography.caption2)
                }
                Button(
                    onClick = {
                        if (isRecording) onCommand(CameraCommand.StopRecord) else onCommand(CameraCommand.StartRecord)
                    },
                    modifier = Modifier.size(60.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (isRecording) RecordRed else Color(0xE6FFFFFF),
                        contentColor = if (isRecording) Color.White else Color.Black,
                    ),
                ) {
                    Text(
                        if (isRecording) "Parar" else "Grabar",
                        style = MaterialTheme.typography.caption2,
                    )
                }
            }
            CompactButton(onClick = onMore) {
                Text("Más", style = MaterialTheme.typography.caption2)
            }
            ScrimText(text = statusText, color = Color.White)
            PageDots(page = page, count = 2)
        }
    }
}

@Composable
private fun AdjustPage(
    frame: ImageBitmap?,
    isRecording: Boolean,
    controls: CameraControls,
    onCommand: (CameraCommand) -> Unit,
    onDone: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (frame != null) {
            Image(
                bitmap = frame,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.28f),
                contentScale = ContentScale.Crop,
            )
        }
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Text("Ajustes", style = MaterialTheme.typography.title3, color = Color.White)
            }
            if (controls.zoomSupported) {
                item {
                    Stepper(
                        value = controls.zoomLabel(),
                        minusEnabled = controls.zoomRatio > controls.minZoom + 0.03f,
                        plusEnabled = controls.zoomRatio < controls.maxZoom - 0.03f,
                        onMinus = { onCommand(CameraCommand.ZoomOut) },
                        onPlus = { onCommand(CameraCommand.ZoomIn) },
                    )
                }
            }
            if (controls.flashControlVisible) {
                val next = controls.flashMode.next(controls.flashSupported, controls.torchSupported)
                item {
                    Chip(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { if (next != null) onCommand(CameraCommand.SetFlash(next)) },
                        enabled = next != null,
                        label = { Text(controls.flashMode.chipLabel()) },
                        secondaryLabel = { Text("Toca para cambiar") },
                        colors = ChipDefaults.secondaryChipColors(),
                    )
                }
            }
            if (controls.exposureSupported) {
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Exposición", color = Color.White, style = MaterialTheme.typography.caption2)
                        Stepper(
                            value = controls.exposureValue(),
                            minusEnabled = controls.exposureIndex > controls.exposureMin,
                            plusEnabled = controls.exposureIndex < controls.exposureMax,
                            onMinus = { onCommand(CameraCommand.ExposureDown) },
                            onPlus = { onCommand(CameraCommand.ExposureUp) },
                        )
                    }
                }
            }
            if (controls.canSwitchCamera) {
                val target = if (controls.facing == CameraFacing.BACK) "frontal" else "trasera"
                item {
                    Chip(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onCommand(CameraCommand.SwitchCamera) },
                        enabled = !isRecording,
                        label = { Text("Cambiar a $target") },
                        secondaryLabel = {
                            Text(
                                if (isRecording) {
                                    "No disponible al grabar"
                                } else if (controls.facing == CameraFacing.BACK) {
                                    "Ahora: trasera"
                                } else {
                                    "Ahora: frontal"
                                },
                            )
                        },
                        colors = ChipDefaults.secondaryChipColors(),
                    )
                }
            }
            if (!controls.zoomSupported && !controls.flashControlVisible &&
                !controls.exposureSupported && !controls.canSwitchCamera
            ) {
                item {
                    Text(
                        "Esperando la cámara…",
                        color = Color.White,
                        style = MaterialTheme.typography.caption2,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDone,
                    label = { Text("Listo") },
                    colors = ChipDefaults.primaryChipColors(),
                )
            }
        }
    }
}

@Composable
private fun Stepper(
    value: String,
    minusEnabled: Boolean,
    plusEnabled: Boolean,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = onMinus,
            enabled = minusEnabled,
            modifier = Modifier.size(48.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xE6121212)),
        ) {
            Text("−", color = Color.White)
        }
        Text(
            text = value,
            color = Color.White,
            style = MaterialTheme.typography.title3,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(min = 52.dp)
                .clip(CircleShape)
                .background(Scrim)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
        Button(
            onClick = onPlus,
            enabled = plusEnabled,
            modifier = Modifier.size(48.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xE6121212)),
        ) {
            Text("+", color = Color.White)
        }
    }
}

@Composable
private fun ScrimText(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.caption2,
        textAlign = TextAlign.Center,
        maxLines = 2,
        modifier = Modifier
            .clip(CircleShape)
            .background(Scrim)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun PageDots(page: Int, count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .size(if (index == page) 6.dp else 4.dp)
                    .clip(CircleShape)
                    .background(if (index == page) Color.White else Color(0x66FFFFFF)),
            )
        }
    }
}
