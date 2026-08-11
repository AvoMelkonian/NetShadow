package com.example.netshadow.capture.reader

import android.util.Log
import java.util.ArrayDeque

class BufferPool(
    private val bufferSize: Int,
    private val initialCapacity: Int = 10
) {
    private val pool = ArrayDeque<ByteArray>(initialCapacity)

    @Synchronized
    fun acquire(): ByteArray {
        return if (pool.isNotEmpty()) {
            pool.removeFirst()
        } else {
            Log.w(TAG, "Pool exhausted, allocating fresh buffer of size $bufferSize")
            ByteArray(bufferSize)
        }
    }

    @Synchronized
    fun release(buffer: ByteArray) {
        if (buffer.size != bufferSize) {
            Log.e(TAG, "Attempted to release buffer of incorrect size: ${buffer.size}")
            return
        }
        pool.addLast(buffer)
    }

    @Synchronized
    fun currentSize(): Int = pool.size

    companion object {
        private const val TAG = "BufferPool"
    }
}
