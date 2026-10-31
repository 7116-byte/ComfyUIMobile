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

    @Test fun rejectsUnsupportedSchemesAndMalformedAddresses() {
        listOf("ftp://192.168.1.2:8188", "http://", "not a valid host", "javascript:alert(1)").forEach { value ->
            assertTrue(runCatching { LanAddress.normalize(value) }.isFailure)
        }
        assertFalse(LanAddress.isTrustedHost(""))
    }

    @Test fun acceptsCloudHttpsWithoutAddingComfyPortAndDeduplicatesDefaultTlsPort() {
        assertEquals("https://abc123-8188.proxy.runpod.net", LanAddress.normalize("https://abc123-8188.proxy.runpod.net/"))
        assertEquals("https://comfy.example.com", LanAddress.normalize("HTTPS://Comfy.Example.COM:443/"))
        assertEquals("https://comfy.example.com:8443", LanAddress.normalize("https://comfy.example.com:8443"))
        val autoDl = "https://u-example.region.seetacloud.com:8443"
        assertEquals(autoDl, LanAddress.normalize("$autoDl/"))
        assertEquals("wss://u-example.region.seetacloud.com:8443/ws?clientId=mobile", LanAddress.webSocketUrl(autoDl, "mobile"))
        assertEquals("http://192.168.1.20:8188", LanAddress.normalize("http://192.168.1.20"))
        assertEquals("http://comfy.example.com:80", LanAddress.normalize("http://comfy.example.com:80"))
        assertEquals("comfy.example.com", LanAddress.serverHost("https://comfy.example.com:8443"))
    }

    @Test fun createsSecureWebSocketUrlAndPreservesNonDefaultPorts() {
        assertEquals("wss://comfy.example.com/ws?clientId=mobile+1", LanAddress.webSocketUrl("https://comfy.example.com", "mobile 1"))
        assertEquals("wss://comfy.example.com:8443/ws?clientId=mobile", LanAddress.webSocketUrl("https://comfy.example.com:8443", "mobile"))
        assertEquals("ws://192.168.1.20:8188/ws?clientId=mobile", LanAddress.webSocketUrl("http://192.168.1.20:8188", "mobile"))
        assertEquals("https://[::1]:8443", LanAddress.normalize("https://[::1]:8443/"))
    }

    @Test fun sameOriginRejectsLookalikeDomainsDifferentPortsAndTlsDowngrades() {
        assertTrue(LanAddress.sameOrigin("https://COMFY.example.com:443/index.html", "https://comfy.example.com"))
        listOf("https://comfy.example.com.evil.test/", "https://comfy.example.com:8443/", "http://comfy.example.com/", "https://comfy.example.com@evil.test/", "about:blank").forEach { target ->
            assertFalse(LanAddress.sameOrigin(target, "https://comfy.example.com"))
        }
    }

    @Test fun createsCompleteSlash24Subnet() {
        val addresses = LanAddress.subnet24("192.168.7.20")
        assertEquals(254, addresses.size)
        assertEquals("192.168.7.1", addresses.first())
        assertEquals("192.168.7.254", addresses.last())
    }

    @Test fun rejectsProxyPathsAndCredentialsInsteadOfSilentlyDroppingThem() {
        listOf("http://example.org:80/comfy", "http://example.org:80/?token=secret", "http://user:password@example.org:80").forEach { address ->
            assertTrue(runCatching { LanAddress.normalize(address) }.isFailure)
        }
        assertEquals("http://example.org:80", LanAddress.normalize("http://example.org:80/"))
    }
}
