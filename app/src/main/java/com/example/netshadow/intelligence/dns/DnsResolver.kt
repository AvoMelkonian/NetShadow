package com.example.netshadow.intelligence.dns

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.util.Collections

interface DnsResolver {
    suspend fun reverseLookup(ipAddress: String): String?
}

object DefaultDnsResolver : DnsResolver {
    private val cache = Collections.synchronizedMap(object : LinkedHashMap<String, String>(100, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            return size > 500
        }
    })

    override suspend fun reverseLookup(ipAddress: String): String? = withContext(Dispatchers.IO) {
        cache[ipAddress]?.let { return@withContext it }

        try {
            val inetAddress = InetAddress.getByName(ipAddress)
            val hostname = inetAddress.canonicalHostName
            
            if (hostname != ipAddress) {
                cache[ipAddress] = hostname
                return@withContext hostname
            }
        } catch (e: Exception) {
            Log.w("DnsResolver", "Reverse DNS lookup failed for $ipAddress: ${e.message}")
        }
        null
    }
}
