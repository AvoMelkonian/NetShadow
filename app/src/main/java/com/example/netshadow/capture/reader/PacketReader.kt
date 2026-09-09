package com.example.netshadow.capture.reader

import android.os.ParcelFileDescriptor
import android.util.Log
import android.system.Os
import android.system.OsConstants
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import java.io.FileInputStream
import java.io.FileOutputStream

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
    private val outputStream = FileOutputStream(vpnInterface.fileDescriptor)
    
    // The Shim FD for tun2socks integration
    private var shimFd: java.io.FileDescriptor? = null
    private var shimInputStream: FileInputStream? = null
    private var shimOutputStream: FileOutputStream? = null
    
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
        
        setupShim()
        Log.i(TAG, "Starting PacketReader with valid FD and Shim")

        job = scope.launch(Dispatchers.IO) {
            val inputStream = FileInputStream(fd)
            
            try {
                while (isActive) {
                    val buffer = bufferPool.acquire()
                    val readLength = try {
                        inputStream.read(buffer)
                    } catch (e: Exception) {
                        bufferPool.release(buffer)
                        throw e
                    }
                    
                    if (readLength > 0) {
                        packetChannel.send(RawPacket(buffer, readLength))
                    } else {
                        bufferPool.release(buffer)
                        if (readLength == -1) {
                            Log.i(TAG, "End of stream reached (EOF)")
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                // If we are not active, this exception is likely due to the FD being closed
                // during shutdown, which is expected.
                if (isActive) {
                    Log.e(TAG, "Unexpected error in PacketReader loop", e)
                } else {
                    Log.i(TAG, "PacketReader loop unblocked by closure (expected during shutdown)")
                }
            } finally {
                packetChannel.close()
                Log.i(TAG, "PacketReader loop stopped")
            }
        }
    }

    /**
     * Writes a raw packet back to the TUN interface.
     */
    fun writePacket(data: ByteArray) {
        try {
            outputStream.write(data)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing packet to TUN", e)
        }
    }

    /**
     * Writes a raw packet back to the TUN interface with specific length.
     */
    fun writePacket(data: ByteArray, length: Int) {
        try {
            outputStream.write(data, 0, length)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing packet to TUN", e)
        }
    }

    /**
     * Releases a buffer back to the reader's pool. 
     * Consumers MUST call this after processing a [RawPacket].
     */
    fun releaseBuffer(buffer: ByteArray) {
        bufferPool.release(buffer)
    }

    private fun setupShim() {
        try {
            // Create a Unix Socket Pair for the tun2socks shim
            val myFd = java.io.FileDescriptor()
            val theirFd = java.io.FileDescriptor()
            Os.socketpair(OsConstants.AF_UNIX, OsConstants.SOCK_SEQPACKET, 0, myFd, theirFd)

            shimFd = myFd
            shimInputStream = FileInputStream(myFd)
            shimOutputStream = FileOutputStream(myFd)

            // Return the 'theirFd' integer to be used by Tun2SocksEngine
            _theirFd = theirFd
            
            // Start a loop to read from tun2socks and write back to the real TUN
            scope.launch(Dispatchers.IO) {
                val buffer = ByteArray(BUFFER_SIZE)
                try {
                    while (isActive) {
                        val read = shimInputStream?.read(buffer) ?: -1
                        if (read > 0) {
                            writePacket(buffer, read)
                        } else if (read == -1) break
                    }
                } catch (e: Exception) {
                    if (isActive) Log.e(TAG, "Shim read error", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup shim", e)
        }
    }

    private var _theirFd: java.io.FileDescriptor? = null
    val theirFd: java.io.FileDescriptor? get() = _theirFd

    /**
     * Forwards a packet to the tun2socks engine via the shim.
     */
    fun forwardToShim(data: ByteArray, length: Int) {
        try {
            shimOutputStream?.write(data, 0, length)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to shim", e)
        }
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
