package com.local.comfyuimobile.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanAddressTest {
    @Test fun normalizesPrivateAddressAndDefaultPort() {
        assertEquals("http://192.168.10.109:8188", LanAddress.normalize("192.168.10.109/"))
        assertEquals("http://10.0.0.2:9000", LanAddress.normalize("http://10.0.0.2:9000"))
        assertEquals("http://100.64.0.10:18188", LanAddress.normalize("http://100.64.0.10:18188/"))
        assertEquals("http://8.8.8.8:8188", LanAddress.normalize("8.8.8.8"))
        assertEquals("http://comfy.example.com:18188", LanAddress.normalize("http://comfy.example.com:18188"))
    }

    @Test fun acceptsPrivateVpnSharedAndPublicRanges() {
        assertTrue(LanAddress.isTrustedHost("100.64.0.1"))
        assertTrue(LanAddress.isTrustedHost("198.18.0.1"))
        assertTrue(LanAddress.isTrustedHost("8.8.8.8"))
        assertTrue(LanAddress.isTrustedHost("vpn-comfy.example"))
    }

    @Test fun rejectsHttpsAndMalformedAddresses() {
        listOf("https://192.168.1.2:8188", "http://", "not a valid host").forEach { value ->
            assertTrue(runCatching { LanAddress.normalize(value) }.isFailure)
        }
        assertFalse(LanAddress.isTrustedHost(""))
    }

    @Test fun createsCompleteSlash24Subnet() {
        val addresses = LanAddress.subnet24("192.168.7.20")
        assertEquals(254, addresses.size)
        assertEquals("192.168.7.1", addresses.first())
        assertEquals("192.168.7.254", addresses.last())
    }
}
