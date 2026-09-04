package com.example.netshadow.intelligence.trackers

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.util.Collections

class TrackerMatcher(context: Context) {
    private val trackerDomains: Set<String> by lazy {
        try {
            context.assets.open("trackers.json").use { inputStream ->
                val json = inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(json)
                val trackers = root.getJSONObject("trackers")
                val domains = mutableSetOf<String>()
                trackers.keys().forEach { key ->
                    val tracker = trackers.getJSONObject(key)
                    val networkFilters = tracker.optString("network_filters", "")
                    if (networkFilters.isNotEmpty()) {
                        networkFilters.split("|").forEach { filter ->
                            if (filter.isNotEmpty()) domains.add(filter.lowercase())
                        }
                    }
                }
                domains
            }
        } catch (e: Exception) {
            Log.e("TrackerMatcher", "Failed to load tracker list: ${e.message}")
            emptySet()
        }
    }

    fun isTracker(domain: String): Boolean {
        if (domain.isEmpty()) return false
        val normalizedDomain = domain.lowercase()
        
        // Exact match
        if (trackerDomains.contains(normalizedDomain)) return true
        
        // Suffix match (subdomain matching)
        // e.g., if "doubleclick.net" is in list, "ads.doubleclick.net" should match
        // We iterate through parent domains
        var parts = normalizedDomain.split('.')
        while (parts.size > 1) {
            parts = parts.drop(1)
            val parent = parts.joinToString(".")
            if (trackerDomains.contains(parent)) return true
        }
        
        return false
    }
}
