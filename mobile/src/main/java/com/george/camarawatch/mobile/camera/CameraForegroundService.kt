package com.george.camarawatch.mobile.camera

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.george.camarawatch.mobile.CamaraWatchApp
import com.george.camarawatch.mobile.CommandBus
import com.george.camarawatch.mobile.MainActivity
import com.george.camarawatch.mobile.R
import com.george.camarawatch.mobile.preview.NetworkAddress
import com.george.camarawatch.mobile.preview.PreviewHub
import com.george.camarawatch.mobile.preview.TcpPreviewServer
import com.george.camarawatch.mobile.wear.PhoneWearBridge
import com.george.camarawatch.shared.CameraCommand
import com.george.camarawatch.shared.PhoneStatus
import com.george.camarawatch.shared.Protocol
import com.google.android.gms.wearable.ChannelClient
import kotlinx.coroutines.launch

class CameraForegroundService : LifecycleService() {
    private val hub = PreviewHub()
    private lateinit var camera: RearCameraController
    private lateinit var tcpServer: TcpPreviewServer
    private lateinit var wearBridge: PhoneWearBridge
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var tcpClients = 0
    private var channelClients = 0

    override fun onCreate() {
        super.onCreate()
        startInForeground(PhoneStatus.STARTING.message)
        camera = RearCameraController(
            context = this,
            lifecycleOwner = this,
            hub = hub,
            onStatus = { status, detail -> publishStatus(status, detail) },
            onRecording = { recording ->
                CommandBus.setRecording(recording)
                updateNotification(if (recording) PhoneStatus.RECORDING.message else CommandBus.statusDetail.value)
                lifecycleScope.launch { syncWear() }
            },
        )
        tcpServer = TcpPreviewServer(hub) { count ->
            tcpClients = count
            CommandBus.setPreviewClients(tcpClients + channelClients)
            if (count > 0) {
                publishStatus(PhoneStatus.PREVIEW_WIFI, PhoneStatus.PREVIEW_WIFI.message)
            }
        }
        wearBridge = PhoneWearBridge(
            context = this,
            hub = hub,
            scope = lifecycleScope,
            onChannelClients = { count ->
                channelClients = count
                CommandBus.setPreviewClients(tcpClients + channelClients)
                if (count > 0 && tcpClients == 0) {
                    publishStatus(PhoneStatus.PREVIEW_WEAR, PhoneStatus.PREVIEW_WEAR.message)
                }
            },
        )
        acquireLocks()
        tcpServer.start()
        val ip = NetworkAddress.localIpv4()
        CommandBus.setWifiEndpoint(ip?.let { "$it:${Protocol.PREVIEW_PORT}" })
        CommandBus.setServiceRunning(true)
        activeInstance = this
        pendingChannel?.let {
            wearBridge.onChannelOpened(it)
            pendingChannel = null
        }
        lifecycleScope.launch {
            if (hasCameraPermission()) {
                try {
                    camera.start()
                } catch (error: Exception) {
                    publishStatus(PhoneStatus.CAMERA_UNAVAILABLE, error.message ?: PhoneStatus.CAMERA_UNAVAILABLE.message)
                }
            } else {
                publishStatus(PhoneStatus.NO_CAMERA_PERMISSION, PhoneStatus.NO_CAMERA_PERMISSION.message)
            }
            syncWear()
        }
        lifecycleScope.launch {
            CommandBus.commands.collect { handleCommand(it) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val raw = intent?.getStringExtra(EXTRA_COMMAND)
        CameraCommand.fromWire(raw)?.let { CommandBus.emit(it) }
        pendingChannel?.let {
            wearBridge.onChannelOpened(it)
            pendingChannel = null
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        activeInstance = null
        tcpServer.stop()
        wearBridge.close()
        camera.release()
        wifiLock?.let { if (it.isHeld) it.release() }
        wakeLock?.let { if (it.isHeld) it.release() }
        CommandBus.setServiceRunning(false)
        CommandBus.setRecording(false)
        CommandBus.setPreviewClients(0)
        CommandBus.setStatus(PhoneStatus.STOPPED)
        super.onDestroy()
    }

    fun attachPreview(provider: androidx.camera.core.Preview.SurfaceProvider) {
        camera.previewUseCase.setSurfaceProvider(provider)
    }

    fun bindOpenedChannel(channel: ChannelClient.Channel) {
        wearBridge.onChannelOpened(channel)
    }

    fun closeChannel(channel: ChannelClient.Channel) {
        wearBridge.onChannelClosed(channel)
    }

    private fun handleCommand(command: CameraCommand) {
        when (command) {
            CameraCommand.TAKE_PHOTO -> camera.takePhoto()
            CameraCommand.START_RECORD -> camera.startRecording()
            CameraCommand.STOP_RECORD -> camera.stopRecording()
        }
    }

    private fun publishStatus(status: PhoneStatus, detail: String) {
        CommandBus.setStatus(status, detail)
        updateNotification(detail)
        lifecycleScope.launch { syncWear() }
    }

    private suspend fun syncWear() {
        runCatching {
            wearBridge.publishPreviewInfo(
                ip = NetworkAddress.localIpv4(),
                port = Protocol.PREVIEW_PORT,
                status = CommandBus.status.value,
                recording = CommandBus.recording.value,
            )
            wearBridge.broadcastStatus(CommandBus.status.value, CommandBus.recording.value)
        }
    }

    private fun startInForeground(text: String) {
        val notification = notification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            val hasMic = ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (hasMic) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, types)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, notification(text))
    }

    private fun notification(text: String) = NotificationCompat.Builder(this, CamaraWatchApp.CHANNEL_ID)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(text)
        .setSmallIcon(R.drawable.ic_stat_camera)
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .build()

    private fun acquireLocks() {
        val wifi = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "CamaraWatch:wifi").apply {
            setReferenceCounted(false)
            acquire()
        }
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CamaraWatch:cpu").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        const val EXTRA_COMMAND = "command"
        const val NOTIFICATION_ID = 1101
        @Volatile
        var pendingChannel: ChannelClient.Channel? = null
        @Volatile
        var activeInstance: CameraForegroundService? = null

        fun onExternalChannelClosed(channel: ChannelClient.Channel) {
            activeInstance?.closeChannel(channel)
        }
    }
}

