package com.example.netshadow.capture.relay

import android.util.Log

/**
 * JNI Wrapper for the tun2socks engine.
 * 
 * IMPORTANT: Because the 'tun2socks.aar' is a 3rd-party binary, the exact 
 * class name and method signature can vary. 
 * 
 * If you see 'Unresolved reference', please check the AAR's documentation 
 * or use Android Studio's "External Libraries" view to find the correct 
 * package name (e.g., 'com.github.xjasonlyu.tun2socks.Tun2socks').
 */
object Tun2SocksEngine {
    
    private var isRunning = false

    /**
     * Starts the tun2socks engine.
     */
    fun start(fd: Int, proxy: String = "socks5://127.0.0.1:1080") {
        if (isRunning) return
        
        try {
            Log.i(TAG, "Starting tun2socks engine on FD: $fd with proxy: $proxy")
            
            // This is the common entry point for gomobile-bound Go libraries.
            // Replace 'tun2socks.Tun2socks' with the actual generated class name.
            // tun2socks.Tun2socks.start(fd.toLong(), proxy, ...)
            
            isRunning = true
            Log.w(TAG, "TUN2SOCKS START CALLED (Verify AAR package name if traffic doesn't flow)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start tun2socks", e)
        }
    }

    fun stop() {
        if (!isRunning) return
        try {
            Log.i(TAG, "Stopping tun2socks engine")
            // tun2socks.Tun2socks.stop()
            isRunning = false
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping tun2socks", e)
        }
    }

    private const val TAG = "Tun2SocksEngine"
}
