package com.example.netshadow.capture.reader

import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import java.io.FileInputStream

/**
 * Represents a raw packet read from the TUN interface.
 * [data] is the buffer from the pool, [length] is the actual bytes read.
 */
data class RawPacket(
    val data: ByteArray,
    val length: Int
)

class PacketReader(
    private val vpnInterface: ParcelFileDescriptor,
    private val scope: CoroutineScope
) {
    private var job: Job? = null
    private val bufferPool = BufferPool(BUFFER_SIZE)
    
    // Using a buffered channel for backpressure. 
    // Capacity 100 provides a small buffer for spikes without excessive memory use.
    private val packetChannel = Channel<RawPacket>(100)

    val packets: ReceiveChannel<RawPacket> = packetChannel

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
                        // Push to channel. This will suspend if the channel is full (backpressure).
                        packetChannel.send(RawPacket(buffer, readLength))
                    } else {
                        // Release immediately if no data read
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
                packetChannel.close()
                Log.i(TAG, "PacketReader loop stopped")
            }
        }
    }

    /**
     * Releases a buffer back to the reader's pool. 
     * Consumers MUST call this after processing a [RawPacket].
     */
    fun releaseBuffer(buffer: ByteArray) {
        bufferPool.release(buffer)
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
