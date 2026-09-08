package com.george.camarawatch.mobile.preview

import android.util.Log
import com.george.camarawatch.shared.PreviewCodec
import com.george.camarawatch.shared.Protocol
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class TcpPreviewServer(
    private val hub: PreviewHub,
    private val onClientCount: (Int) -> Unit,
) {
    private val running = AtomicBoolean(false)
    private val clients = CopyOnWriteArrayList<Socket>()
    private var server: ServerSocket? = null
    private var acceptThread: Thread? = null

    val port: Int get() = Protocol.PREVIEW_PORT

    fun start() {
        if (!running.compareAndSet(false, true)) return
        acceptThread = thread(name = "cw-tcp-accept", isDaemon = true) {
            try {
                ServerSocket(Protocol.PREVIEW_PORT).use { socket ->
                    server = socket
                    socket.reuseAddress = true
                    while (running.get()) {
                        val client = try {
                            socket.accept()
                        } catch (_: Exception) {
                            if (!running.get()) break
                            continue
                        }
                        client.tcpNoDelay = true
                        clients.add(client)
                        onClientCount(clients.size)
                        thread(name = "cw-tcp-client", isDaemon = true) {
                            serve(client)
                        }
                    }
                }
            } catch (error: Exception) {
                Log.w(TAG, "Servidor TCP detenido: ${error.message}")
            } finally {
                running.set(false)
            }
        }
    }

    fun stop() {
        running.set(false)
        clients.forEach { runCatching { it.close() } }
        clients.clear()
        runCatching { server?.close() }
        server = null
        acceptThread = null
        onClientCount(0)
    }

    private fun serve(client: Socket) {
        try {
            val output = client.getOutputStream()
            var lastSent: ByteArray? = null
            while (running.get() && !client.isClosed) {
                val frame = hub.latestOrNull()
                if (frame != null && frame !== lastSent) {
                    PreviewCodec.writeFrame(output, frame)
                    lastSent = frame
                }
                Thread.sleep(Protocol.PREVIEW_MIN_INTERVAL_MS)
            }
        } catch (error: Exception) {
            Log.d(TAG, "Cliente TCP cerrado: ${error.message}")
        } finally {
            runCatching { client.close() }
            clients.remove(client)
            onClientCount(clients.size)
        }
    }

    companion object {
        private const val TAG = "TcpPreviewServer"
    }
}
