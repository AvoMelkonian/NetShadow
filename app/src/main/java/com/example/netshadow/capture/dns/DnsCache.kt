package com.example.netshadow.capture.dns

import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * A simple IP-to-Domain cache to enrich connection events.
 * 
 * Policy:
 * - Thread-safe ConcurrentHashMap for O(1) lookups.
 * - Simple size-based clearing: if the cache exceeds MAX_SIZE, clear it to prevent memory leaks.
 * - Records are populated from DNS responses.
 */
class DnsCache {
    private val cache = ConcurrentHashMap<InetAddress, String>()

    fun put(address: InetAddress, domain: String) {
        if (cache.size > MAX_SIZE) {
            cache.clear()
        }
        cache[address] = domain
    }

    fun get(address: InetAddress): String? {
        return cache[address]
    }

    companion object {
        private const val MAX_SIZE = 1000
    }
}
