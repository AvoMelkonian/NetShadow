package com.example.netshadow.intelligence.geoip

import android.content.Context
import android.util.Log
import com.maxmind.geoip2.DatabaseReader
import java.net.InetAddress
import java.util.Collections

open class GeoIpService(context: Context) {
    private val countryDbReader: DatabaseReader? by lazy {
        try {
            context.assets.open("GeoLite2-Country.mmdb").use { inputStream ->
                DatabaseReader.Builder(inputStream).build()
            }
        } catch (e: Exception) {
            Log.e("GeoIpService", "Failed to load Country database: ${e.message}")
            null
        }
    }

    private val asnDbReader: DatabaseReader? by lazy {
        try {
            context.assets.open("GeoLite2-ASN.mmdb").use { inputStream ->
                DatabaseReader.Builder(inputStream).build()
            }
        } catch (e: Exception) {
            Log.e("GeoIpService", "Failed to load ASN database: ${e.message}")
            null
        }
    }

    private val countryCache = Collections.synchronizedMap(object : LinkedHashMap<String, String>(100, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            return size > 500
        }
    })

    private val asnCache = Collections.synchronizedMap(object : LinkedHashMap<String, AsnInfo>(100, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AsnInfo>?): Boolean {
            return size > 500
        }
    })

    data class AsnInfo(val asn: Int, val organization: String)

    open fun getCountryCode(ipAddress: String): String? {
        if (isPrivateIp(ipAddress)) return "LOCAL"
        
        countryCache[ipAddress]?.let { return it }

        return try {
            val inetAddress = InetAddress.getByName(ipAddress)
            val response = countryDbReader?.country(inetAddress)
            val countryCode = response?.country?.isoCode
            if (countryCode != null) {
                countryCache[ipAddress] = countryCode
            }
            countryCode
        } catch (e: Exception) {
            null
        }
    }

    open fun getAsnInfo(ipAddress: String): AsnInfo? {
        if (isPrivateIp(ipAddress)) return null
        
        asnCache[ipAddress]?.let { return it }

        return try {
            val inetAddress = InetAddress.getByName(ipAddress)
            val response = asnDbReader?.asn(inetAddress)
            val asn = response?.autonomousSystemNumber?.toInt()
            val org = response?.autonomousSystemOrganization
            
            if (asn != null && org != null) {
                val info = AsnInfo(asn, org)
                asnCache[ipAddress] = info
                info
            } else null
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
