package com.local.comfyuimobile.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanAddressTest {
    @Test fun normalizesPrivateAddressAndDefaultPort() {
        assertEquals("http://192.168.10.109:8188", LanAddress.normalize("192.168.10.109/"))
        assertEquals("http://10.0.0.2:9000", LanAddress.normalize("http://10.0.0.2:9000"))
    }

    @Test fun rejectsPublicVirtualAndHttpsAddresses() {
        listOf("8.8.8.8", "198.18.0.1", "https://192.168.1.2:8188").forEach { value ->
            assertTrue(runCatching { LanAddress.normalize(value) }.isFailure)
        }
        assertFalse(LanAddress.isTrustedHost("172.32.0.1"))
        assertFalse(LanAddress.isTrustedHost("example"))
    }

    @Test fun createsCompleteSlash24Subnet() {
        val addresses = LanAddress.subnet24("192.168.7.20")
        assertEquals(254, addresses.size)
        assertEquals("192.168.7.1", addresses.first())
        assertEquals("192.168.7.254", addresses.last())
    }
}
