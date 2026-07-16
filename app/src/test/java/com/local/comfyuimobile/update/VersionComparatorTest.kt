package com.local.comfyuimobile.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparatorTest {
    @Test fun comparesTagsAndMissingComponents() {
        assertTrue(VersionComparator.compare("v0.1.1", "0.1.0") > 0)
        assertTrue(VersionComparator.compare("1.10.0", "1.9.9") > 0)
        assertEquals(0, VersionComparator.compare("v1.2", "1.2.0"))
        assertEquals(0, VersionComparator.compare("1.2.0-beta", "1.2.0"))
    }
}
