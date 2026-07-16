package com.local.comfyuimobile.update

import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVerifierTest {
    @Test fun acceptsOnlySamePackageNewerVersionAndSameCertificate() {
        UpdateVerifier.verifyMetadata("com.local.app", "com.local.app", 1, 2, byteArrayOf(1, 2), byteArrayOf(1, 2))
        assertRejected { UpdateVerifier.verifyMetadata("com.local.app", "evil.app", 1, 2, byteArrayOf(1), byteArrayOf(1)) }
        assertRejected { UpdateVerifier.verifyMetadata("com.local.app", "com.local.app", 2, 2, byteArrayOf(1), byteArrayOf(1)) }
        assertRejected { UpdateVerifier.verifyMetadata("com.local.app", "com.local.app", 1, 2, byteArrayOf(1), byteArrayOf(9)) }
    }

    private fun assertRejected(block: () -> Unit) {
        assertTrue(runCatching(block).exceptionOrNull() is IllegalArgumentException)
    }
}
