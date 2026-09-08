package com.george.camarawatch.wear.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import kotlinx.coroutines.flow.StateFlow

@Composable
fun WatchScreen(
    status: StateFlow<String>,
    recording: StateFlow<Boolean>,
    preview: StateFlow<ImageBitmap?>,
    usingWifi: StateFlow<Boolean>,
    onFoto: () -> Unit,
    onGrabar: () -> Unit,
) {
    val statusText by status.collectAsState()
    val isRecording by recording.collectAsState()
    val frame by preview.collectAsState()
    val wifi by usingWifi.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (frame != null) {
            Image(
                bitmap = frame!!,
                contentDescription = "Vista previa",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = "Sin vista previa",
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
                style = MaterialTheme.typography.body2,
                textAlign = TextAlign.Center,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (isRecording) "GRABANDO" else if (wifi) "Wi‑Fi" else "Cámara Watch",
                color = if (isRecording) Color(0xFFFF4D4D) else Color.White,
                style = MaterialTheme.typography.caption2,
                textAlign = TextAlign.Center,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onFoto,
                    modifier = Modifier.size(52.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xE6121212)),
                ) {
                    Text("Foto", style = MaterialTheme.typography.caption2)
                }
                Button(
                    onClick = onGrabar,
                    modifier = Modifier.size(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (isRecording) Color(0xFFFF4D4D) else Color(0xE6FFFFFF),
                        contentColor = if (isRecording) Color.White else Color.Black,
                    ),
                ) {
                    Text(if (isRecording) "Parar" else "Grabar", style = MaterialTheme.typography.caption2)
                }
            }
            Text(
                text = statusText,
                color = Color.White,
                style = MaterialTheme.typography.caption2,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0x99000000))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}
