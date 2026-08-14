package com.example.netshadow.capture.dns

import android.util.Log

/**
 * Parser for DNS wire-format packets (RFC 1035).
 */
class DnsParser {
    companion object {
        private const val TAG = "DnsParser"

        fun isDnsPacket(destPort: Int): Boolean {
            return destPort == 53
        }

        // TODO: Implement wire-format parsing in next sub-tasks
    }
}
