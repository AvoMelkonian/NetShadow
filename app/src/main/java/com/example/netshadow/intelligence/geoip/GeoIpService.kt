package com.example.netshadow.intelligence.geoip

import android.content.Context
import android.util.Log
import com.maxmind.geoip2.DatabaseReader
import java.net.InetAddress
import java.util.Collections

open class GeoIpService(context: Context) {
    private val dbReader: DatabaseReader? by lazy {
        try {
            context.assets.open("GeoLite2-Country.mmdb").use { inputStream ->
                DatabaseReader.Builder(inputStream).build()
            }
        } catch (e: Exception) {
            Log.e("GeoIpService", "Failed to load GeoIP database: ${e.message}")
            null
        }
    }

    private val cache = Collections.synchronizedMap(object : LinkedHashMap<String, String>(100, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            return size > 500
        }
    })

    open fun getCountryCode(ipAddress: String): String? {
        if (isPrivateIp(ipAddress)) return "LOCAL"
        
        cache[ipAddress]?.let { return it }

        return try {
            val inetAddress = InetAddress.getByName(ipAddress)
            val response = dbReader?.country(inetAddress)
            val countryCode = response?.country?.isoCode
            if (countryCode != null) {
                cache[ipAddress] = countryCode
            }
            countryCode
        } catch (e: Exception) {
            null
        }
    }

    private fun isPrivateIp(ip: String): Boolean {
        return try {
            val addr = InetAddress.getByName(ip)
            addr.isAnyLocalAddress || addr.isLoopbackAddress || addr.isLinkLocalAddress || addr.isSiteLocalAddress
        } catch (e: Exception) {
            false
        }
    }
}
