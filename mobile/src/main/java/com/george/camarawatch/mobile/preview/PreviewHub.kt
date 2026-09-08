package com.george.camarawatch.mobile.preview

import java.util.concurrent.atomic.AtomicReference

class PreviewHub {
    private val latest = AtomicReference<ByteArray?>(null)

    fun update(jpeg: ByteArray) {
        latest.set(jpeg)
    }

    fun latestOrNull(): ByteArray? = latest.get()
}
