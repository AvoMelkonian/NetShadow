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

    fun start() {
        val fd = vpnInterface.fileDescriptor
        if (!fd.valid()) {
            Log.e(TAG, "FileDescriptor is invalid")
            return
        }
        Log.i(TAG, "Starting PacketReader with valid FD")

        job = scope.launch(Dispatchers.IO) {
            val inputStream = FileInputStream(fd)
            val buffer = ByteArray(MAX_PACKET_SIZE)
            
            try {
                while (isActive) {
                    val readLength = inputStream.read(buffer)
                    if (readLength > 0) {
                        // Create a ByteBuffer wrapping the read region
                        val packet = ByteBuffer.wrap(buffer, 0, readLength)
                        Log.d(TAG, "Read packet of length: ${packet.remaining()}")
                        // In Phase 2 Part 2, we will process this packet
                    } else if (readLength == -1) {
                        Log.i(TAG, "End of stream reached")
                        break
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

    fun stop() {
        job?.cancel()
        job = null
    }

    companion object {
        private const val TAG = "PacketReader"
        private const val MAX_PACKET_SIZE = 32767
    }
}
