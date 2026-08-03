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

    @Test fun picksFastestReachableMirror() {
        val apk = "https://github.com/owner/repo/releases/download/v1/app.apk"
        val candidates = UpdateMirrors.candidates(apk, "$apk.sha256")
        val probes = listOf(
            MirrorProbe(candidates[0], latencyMillis = 320, expectedSha = "a".repeat(64)),
            MirrorProbe(candidates[1], latencyMillis = 80, expectedSha = "b".repeat(64)),
            MirrorProbe(candidates[2], latencyMillis = 45, expectedSha = "c".repeat(64)),
        )

        val best = UpdateMirrors.pickFastest(probes)

        assertEquals("GitHub 原地址", best?.candidate?.label)
    }

    @Test fun apiCandidatesCoverDomesticMirrorsThenGithub() {
        val base = "https://api.github.com/repos/o/r/releases/latest"

        val candidates = UpdateMirrors.apiCandidates(base)

        assertTrue(candidates.first().startsWith("https://ghfast.top/"))
        assertEquals(base, candidates.last())
    }
}
