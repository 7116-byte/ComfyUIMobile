package com.local.comfyuimobile.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateMirrorsTest {
    @Test fun prefersDomesticMirrorsAndKeepsGithubFallback() {
        val apk = "https://github.com/owner/repo/releases/download/v1/app.apk"
        val sha = "$apk.sha256"

        val candidates = UpdateMirrors.candidates(apk, sha)

        assertTrue(candidates.first().apkUrl.startsWith("https://ghfast.top/"))
        assertTrue(candidates[1].apkUrl.startsWith("https://ghproxy.net/"))
        assertEquals(apk, candidates.last().apkUrl)
        assertEquals(sha, candidates.last().sha256Url)
    }
}
