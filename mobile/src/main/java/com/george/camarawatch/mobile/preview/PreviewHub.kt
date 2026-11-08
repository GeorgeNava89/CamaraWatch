package com.george.camarawatch.mobile.preview

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class PreviewHub {
    private val latest = AtomicReference<ByteArray?>(null)
    private val consumers = AtomicInteger(0)

    fun update(jpeg: ByteArray) {
        latest.set(jpeg)
    }

    fun latestOrNull(): ByteArray? = latest.get()

    fun setConsumerCount(count: Int) {
        consumers.set(count.coerceAtLeast(0))
    }

    fun hasConsumers(): Boolean = consumers.get() > 0
}
