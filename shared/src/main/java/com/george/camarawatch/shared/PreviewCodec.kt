package com.george.camarawatch.shared

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

object PreviewCodec {
    private const val MAX_FRAME_BYTES = 2 * 1024 * 1024

    fun writeFrame(output: OutputStream, jpeg: ByteArray) {
        val data = DataOutputStream(output)
        data.writeInt(jpeg.size)
        data.write(jpeg)
        data.flush()
    }

    fun readFrame(input: InputStream): ByteArray {
        val data = DataInputStream(input)
        val size = data.readInt()
        if (size <= 0 || size > MAX_FRAME_BYTES) {
            throw IOException("Marco JPEG inválido: $size bytes")
        }
        val jpeg = ByteArray(size)
        data.readFully(jpeg)
        return jpeg
    }
}
