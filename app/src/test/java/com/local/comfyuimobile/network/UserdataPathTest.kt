package com.local.comfyuimobile.network

import org.junit.Assert.assertEquals
import org.junit.Test

class UserdataPathTest {
    @Test fun encodesChineseAndSpacesPerPathSegment() {
        assertEquals(
            "%E5%B7%A5%E4%BD%9C%E6%B5%81%2F%E6%88%91%E7%9A%84%20Krea2.json",
            UserdataPath.encode("工作流/我的 Krea2.json"),
        )
    }

    @Test fun acceptsWindowsSeparatorsWithoutEncodingSlash() {
        assertEquals("folder%2Fchild.json", UserdataPath.encode("folder\\child.json"))
    }
}
