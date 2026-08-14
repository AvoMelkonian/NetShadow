package com.example.netshadow.capture.attribution

import android.content.Context
import android.net.ConnectivityManager
import android.os.Process
import android.util.Log
import java.net.InetSocketAddress

/**
 * Handles UID resolution for captured network traffic.
 * Uses ConnectivityManager.getConnectionOwnerUid to attribute packets to specific apps.
 */
class TrafficAttributor(context: Context) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     * Resolves the UID owning a connection.
     * 
     * @param protocol The IANA protocol number (e.g., 6 for TCP, 17 for UDP).
     * @param local The local address and port.
     * @param remote The remote address and port.
     * @return The UID of the owning process, or [Process.INVALID_UID] if not found.
     * 
     * Note on Retry Policy: Currently uses a single-attempt strategy to maintain low latency 
     * in the packet processing hot-path. Races during socket setup/teardown may result in 
     * temporary attribution failures (-1), which are tagged as UNATTRIBUTED.
     */
    fun getUid(protocol: Int, local: InetSocketAddress, remote: InetSocketAddress): Int {
        // API 29+ requirement is met by project's minSdk.
        return try {
            val uid = connectivityManager.getConnectionOwnerUid(protocol, local, remote)
            if (uid == Process.INVALID_UID) {
                Log.v(TAG, "No UID found for $protocol $local -> $remote")
            }
            uid
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing permission for UID resolution. Ensure app is a VPN service.", e)
            Process.INVALID_UID
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during UID resolution", e)
            Process.INVALID_UID
        }
    }

    companion object {
        private const val TAG = "TrafficAttributor"
    }
}
