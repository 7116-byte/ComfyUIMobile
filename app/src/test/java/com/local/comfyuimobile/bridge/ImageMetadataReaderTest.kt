package com.local.comfyuimobile.bridge

import android.app.Application
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Implementation
import java.io.File
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, shadows = [AvifRetrieverShadow::class])
class ImageMetadataReaderTest {
    @Test fun readsComfyUiExifFromWebpAndAvifWithoutLoadingFrontend() {
        // Pillow-generated one-pixel fixtures with ComfyUI's Model/Make metadata tags.
        val fixtures = listOf(
            "UklGRsQAAABXRUJQVlA4WAoAAAAIAAAAAAAAAAAAVlA4IBgAAAAwAQCdASoBAAEAAUAmJaQAA3AA/v02aABFWElGhgAAAE1NACoAAAAIAAIBDwACAAAAFgAAACYBEAACAAAASQAAADwAAAAAd29ya2Zsb3c6eyJub2RlcyI6W119AHByb21wdDp7IjciOnsiY2xhc3NfdHlwZSI6IktTYW1wbGVyIiwiaW5wdXRzIjp7InNlZWQiOjEyMzQ1Njc4OTAxMjM0NX19fQAA",
            "AAAAIGZ0eXBhdmlmAAAAAGF2aWZtaWYxbWlhZk1BMUIAAAEsbWV0YQAAAAAAAAAhaGRscgAAAAAAAAAAcGljdAAAAAAAAAAAAAAAAAAAAAAOcGl0bQAAAAAAAQAAACxpbG9jAAAAAEQAAAIAAQAAAAEAAAHkAAAAIgACAAAAAQAAAVQAAACQAAAAQWlpbmYAAAAAAAIAAAAaaW5mZQIAAAAAAQAAYXYwMUNvbG9yAAAAABlpbmZlAgAAAAACAABFeGlmRXhpZgAAAAAaaXJlZgAAAAAAAAAOY2RzYwACAAEAAQAAAGppcHJwAAAAS2lwY28AAAAUaXNwZQAAAAAAAAABAAAAAQAAABBwaXhpAAAAAAMICAgAAAAMYXYxQ4EADAAAAAATY29scm5jbHgAAQANAAaAAAAAF2lwbWEAAAAAAAAAAQABBAECgwQAAAC6bWRhdAAAAAZFeGlmAABNTQAqAAAACAACAQ8AAgAAABYAAAAmARAAAgAAAEkAAAA8AAAAAHdvcmtmbG93Onsibm9kZXMiOltdfQBwcm9tcHQ6eyI3Ijp7ImNsYXNzX3R5cGUiOiJLU2FtcGxlciIsImlucHV0cyI6eyJzZWVkIjoxMjM0NTY3ODkwMTIzNDV9fX0AABIACggYAAaICGg0IDIUFMeHhmUCCCCeQAAAkEuyuaxgLsA=",
        )
        fixtures.forEach { fixture ->
            val file = File.createTempFile("metadata", ".img", RuntimeEnvironment.getApplication().cacheDir)
            try {
                file.writeBytes(Base64.getDecoder().decode(fixture))
                val metadata = ImageMetadataReader.read(file)
                assertEquals("""{"7":{"class_type":"KSampler","inputs":{"seed":123456789012345}}}""", metadata.promptJson)
                assertEquals("""{"nodes":[]}""", metadata.workflowJson)
            } finally {
                file.delete()
            }
        }
    }
}

/** Robolectric does not index AVIF. Supply only the native retriever boundary;
 * AndroidX still reads and validates the fixture's real EXIF bytes. */
@Implements(MediaMetadataRetriever::class)
class AvifRetrieverShadow {
    @Implementation fun setDataSource(source: MediaDataSource) = Unit
    @Implementation fun extractMetadata(key: Int): String? = when (key) {
        MediaMetadataRetriever.METADATA_KEY_EXIF_OFFSET -> "344"
        MediaMetadataRetriever.METADATA_KEY_EXIF_LENGTH -> "140"
        else -> null
    }
    @Implementation fun release() = Unit
}
