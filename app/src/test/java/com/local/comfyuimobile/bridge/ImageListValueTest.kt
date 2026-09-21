package com.local.comfyuimobile.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageListValueTest {
    @Test fun preservesSelectionOrderAndEscapesPaths() {
        val encoded = ImageListValue.encode(listOf("目录/a.png", "b\".png"))
        assertEquals(listOf("目录/a.png", "b\".png"), ImageListValue.parse(encoded))
    }

    @Test fun appendsUniquePathsWithoutReplacingTheMainImage() {
        val encoded = ImageListValue.append("[\"main.png\",\"ref.png\"]", listOf("ref.png", "third.png"))
        assertEquals(listOf("main.png", "ref.png", "third.png"), ImageListValue.parse(encoded))
    }

    @Test fun rejectsNonArrayAndNonStringEntries() {
        assertNull(ImageListValue.parse("not json"))
        assertNull(ImageListValue.parse("[\"a.png\",1]"))
    }
}
