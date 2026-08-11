package com.example.netshadow.capture.reader

import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.nio.ByteBuffer

class PacketReader(
    private val vpnInterface: ParcelFileDescriptor,
    private val scope: CoroutineScope
) {
    private var job: Job? = null
    private val bufferPool = BufferPool(BUFFER_SIZE)

    fun start() {
        val fd = vpnInterface.fileDescriptor
        if (!fd.valid()) {
            Log.e(TAG, "FileDescriptor is invalid")
            return
        }
        Log.i(TAG, "Starting PacketReader with valid FD")

        job = scope.launch(Dispatchers.IO) {
            val inputStream = FileInputStream(fd)
            
            try {
                while (isActive) {
                    val buffer = bufferPool.acquire()
                    val readLength = inputStream.read(buffer)
                    
                    if (readLength > 0) {
                        // Log pool size occasionally for debugging
                        if (Math.random() < 0.01) {
                            Log.v(TAG, "Pool size: ${bufferPool.currentSize()}")
                        }
                        
                        // Hand off logic (simulated)
                        processPacket(buffer, readLength)
                        
                        // Release back to pool after "processing"
                        bufferPool.release(buffer)
                    } else {
                        // If no data read or EOF, release the acquired buffer
                        bufferPool.release(buffer)
                        if (readLength == -1) {
                            Log.i(TAG, "End of stream reached")
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(TAG, "Error in PacketReader loop", e)
                }
            } finally {
                Log.i(TAG, "PacketReader loop stopped")
            }
        }
    }

    private fun processPacket(buffer: ByteArray, length: Int) {
        val packet = ByteBuffer.wrap(buffer, 0, length)
        Log.d(TAG, "Read packet of length: ${packet.remaining()}")
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    companion object {
        private const val TAG = "PacketReader"
        private const val BUFFER_SIZE = 1500 // Matches VPN MTU
    }
}
