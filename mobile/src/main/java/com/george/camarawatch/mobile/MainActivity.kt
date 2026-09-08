package com.george.camarawatch.mobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.george.camarawatch.mobile.camera.CameraForegroundService
import com.george.camarawatch.mobile.ui.PhoneScreen
import com.george.camarawatch.mobile.ui.theme.CamaraWatchTheme

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted[Manifest.permission.CAMERA] == true) {
            startCameraService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CamaraWatchTheme {
                val status by CommandBus.statusDetail.collectAsState()
                val recording by CommandBus.recording.collectAsState()
                val endpoint by CommandBus.wifiEndpoint.collectAsState()
                val clients by CommandBus.previewClients.collectAsState()
                val running by CommandBus.serviceRunning.collectAsState()
                PhoneScreen(
                    status = status,
                    recording = recording,
                    wifiEndpoint = endpoint,
                    previewClients = clients,
                    serviceRunning = running,
                    onPreviewReady = { preview -> bindPreview(preview) },
                    onToggleService = { toggleService() },
                )
            }
        }
        requestNeededPermissions()
    }

    override fun onResume() {
        super.onResume()
        if (hasCameraPermission()) startCameraService()
    }

    private fun bindPreview(preview: PreviewView) {
        val service = CameraForegroundService.activeInstance
        if (service != null) {
            service.attachPreview(preview.surfaceProvider)
        }
    }

    private fun toggleService() {
        if (CommandBus.serviceRunning.value) {
            stopService(Intent(this, CameraForegroundService::class.java))
        } else if (hasCameraPermission()) {
            startCameraService()
        } else {
            requestNeededPermissions()
        }
    }

    private fun startCameraService() {
        val intent = Intent(this, CameraForegroundService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun requestNeededPermissions() {
        val needed = buildList {
            if (!hasCameraPermission()) add(Manifest.permission.CAMERA)
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.RECORD_AUDIO)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (needed.isEmpty()) {
            startCameraService()
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
}
