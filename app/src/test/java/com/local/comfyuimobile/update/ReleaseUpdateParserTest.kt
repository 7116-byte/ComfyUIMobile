package com.local.comfyuimobile.update

import android.app.Application
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReleaseUpdateParserTest {
    @Test fun failedRequestsCannotBeReportedAsLatest() {
        assertTrue(runCatching { ReleaseUpdateParser.parse(null, "0.1.69") }.isFailure)
        assertTrue(runCatching { ReleaseUpdateParser.parse(JSONObject("""{"message":"API rate limit exceeded"}"""), "0.1.69") }.isFailure)
    }

    @Test fun published171IsOfferedTo169WithBothRequiredAssets() {
        val info = ReleaseUpdateParser.parse(release(), "0.1.69")!!
        assertEquals("v0.1.71", info.tag)
        assertTrue(info.apkUrl.endsWith("ComfyUIMobile-v0.1.71-release.apk"))
        assertTrue(info.sha256Url!!.endsWith(".apk.sha256"))
        assertNull(ReleaseUpdateParser.parse(release(), "0.1.71"))
    }

    @Test fun incompleteNewReleaseIsAnErrorRatherThanLatest() {
        val root = release()
        root.remove("assets")
        assertTrue(runCatching { ReleaseUpdateParser.parse(root, "0.1.69") }.isFailure)
    }

    @Test fun officialLatestRedirectSupportsApiRateLimitFallback() {
        val page = "https://github.com/7116-byte/ComfyUIMobile/releases/tag/v0.1.71"
        assertEquals("v0.1.71", ReleaseUpdateParser.fromReleasePage(page, "7116-byte/ComfyUIMobile", "0.1.69")!!.tag)
        assertNull(ReleaseUpdateParser.fromReleasePage(page, "7116-byte/ComfyUIMobile", "0.1.71"))
        assertTrue(runCatching { ReleaseUpdateParser.fromReleasePage("https://example.com/releases/tag/v0.1.71", "7116-byte/ComfyUIMobile", "0.1.69") }.isFailure)
        assertTrue(runCatching { ReleaseUpdateParser.fromReleasePage(page + "-beta", "7116-byte/ComfyUIMobile", "0.1.69") }.isFailure)
    }

    private fun release() = JSONObject("""{
        "tag_name":"v0.1.71", "draft":false, "prerelease":false,
        "html_url":"https://github.com/7116-byte/ComfyUIMobile/releases/tag/v0.1.71",
        "assets":[
          {"name":"ComfyUIMobile-v0.1.71-release.apk", "browser_download_url":"https://github.com/7116-byte/ComfyUIMobile/releases/download/v0.1.71/ComfyUIMobile-v0.1.71-release.apk"},
          {"name":"ComfyUIMobile-v0.1.71-release.apk.sha256", "browser_download_url":"https://github.com/7116-byte/ComfyUIMobile/releases/download/v0.1.71/ComfyUIMobile-v0.1.71-release.apk.sha256"}
        ]} """)
}
