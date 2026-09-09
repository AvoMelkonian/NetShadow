package com.example.netshadow.intelligence.geoip

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class GeoIpLookupTest {

    @Test
    fun testLocalIp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val service = GeoIpService(context)
        
        assertEquals("LOCAL", service.getCountryCode("10.0.0.2"))
        assertEquals("LOCAL", service.getCountryCode("192.168.1.1"))
    }

    @Test
    fun testIpLookupWithMissingDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val service = GeoIpService(context)
        
        // Should not crash and return null if DB is missing
        val country = service.getCountryCode("8.8.8.8")
        // In this test environment, it's expected to be null as the asset isn't there.
        // We just want to ensure it doesn't throw.
    }
}
