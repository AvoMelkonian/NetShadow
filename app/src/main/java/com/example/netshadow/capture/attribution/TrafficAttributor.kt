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
    private val packageManager = context.packageManager

    private val systemUidMap = mapOf(
        0 to "root",
        1000 to "system",
        1001 to "phone",
        1013 to "mediaserver",
        1021 to "gps",
        1073 to "network_stack"
    )

    /**
     * Resolves the UID owning a connection.
     * 
     * @param protocol The IANA protocol number (e.g., 6 for TCP, 17 for UDP).
     * @param local The local address and port.
     * @param remote The remote address and port.
     * @return The UID of the owning process, or [Process.INVALID_UID] if not found.
     */
    fun getUid(protocol: Int, local: InetSocketAddress, remote: InetSocketAddress): Int {
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

    /**
     * Resolves a package name for a given UID.
     */
    fun getPackageName(uid: Int): String {
        if (uid == Process.INVALID_UID) return "unattributed"
        if (uid < 10000) return systemUidMap[uid] ?: "system_low_$uid"

        val packages = try {
            packageManager.getPackagesForUid(uid)
        } catch (e: Exception) {
            null
        }

        return when {
            packages == null || packages.isEmpty() -> "unknown_uid_$uid"
            packages.size == 1 -> packages[0]
            else -> "${packages[0]} (+${packages.size - 1} others)" // Handle shared UIDs
        }
    }

    companion object {
        private const val TAG = "TrafficAttributor"
    }
}
