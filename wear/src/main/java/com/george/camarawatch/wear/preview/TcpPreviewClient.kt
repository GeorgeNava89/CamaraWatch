package com.george.camarawatch.wear.preview

import android.util.Log
import com.george.camarawatch.shared.PreviewCodec
import com.george.camarawatch.shared.Protocol
import java.net.InetSocketAddress
import java.net.Socket

class TcpPreviewClient {
    fun connect(ip: String, port: Int, onFrame: (ByteArray) -> Unit, shouldStop: () -> Boolean) {
        val socket = Socket()
        try {
            socket.tcpNoDelay = true
            socket.soTimeout = Protocol.TCP_SO_TIMEOUT_MS
            socket.connect(InetSocketAddress(ip, port), Protocol.TCP_CONNECT_TIMEOUT_MS)
            val input = socket.getInputStream()
            while (!shouldStop() && !socket.isClosed) {
                val jpeg = PreviewCodec.readFrame(input)
                onFrame(jpeg)
            }
        } finally {
            runCatching { socket.close() }
        }
    }

    companion object {
        const val TAG = "TcpPreviewClient"
        fun tryConnect(
            ip: String,
            port: Int,
            onFrame: (ByteArray) -> Unit,
            shouldStop: () -> Boolean,
        ): Boolean {
            return try {
                TcpPreviewClient().connect(ip, port, onFrame, shouldStop)
                true
            } catch (error: Exception) {
                Log.d(TAG, "TCP no disponible: ${error.message}")
                false
            }
        }
    }
}
