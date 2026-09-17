package com.local.comfyuimobile.update

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateFilesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun internalDirectoryIsEmptyBeforeEveryDownload() {
        val directory = temporary.newFolder("cache", "updates")
        File(directory, "old.apk").writeText("old")
        File(directory, "interrupted.part").writeText("partial")
        File(directory, "nested").apply { mkdir(); resolve("piece").writeText("piece") }

        UpdateFiles.prepareInternalDirectory(directory)

        assertTrue(directory.isDirectory)
        assertEquals(emptyList<String>(), directory.listFiles()!!.map(File::getName))
    }

    @Test fun internalDirectoryIsCreatedWhenMissing() {
        val directory = File(temporary.root, "new-cache/updates")

        UpdateFiles.prepareInternalDirectory(directory)

        assertTrue(directory.isDirectory)
    }

    @Test fun legacyCleanupOnlyDeletesAppUpdatePackages() {
        val directory = temporary.newFolder("legacy-downloads")
        val oldApk = File(directory, "ComfyUIMobile-v0.1.59-release.apk").apply { writeText("apk") }
        val partial = File(directory, "ComfyUIMobile-v0.1.60-release.apk.part").apply { writeText("part") }
        val checksum = File(directory, "ComfyUIMobile-v0.1.58-release.apk.sha256").apply { writeText("sha") }
        val userApk = File(directory, "another-app.apk").apply { writeText("keep") }
        val note = File(directory, "notes.txt").apply { writeText("keep") }

        UpdateFiles.cleanLegacyDirectory(directory)

        assertFalse(oldApk.exists())
        assertFalse(partial.exists())
        assertFalse(checksum.exists())
        assertTrue(userApk.exists())
        assertTrue(note.exists())
    }

    @Test fun progressIsDeterminateOnlyWhenLengthIsKnown() {
        assertEquals(0.25f, UpdateDownloadProgress(25, 100).fraction!!, 0.0001f)
        assertEquals(1f, UpdateDownloadProgress(120, 100).fraction!!, 0.0001f)
        assertNull(UpdateDownloadProgress(25, -1).fraction)
        assertNull(UpdateDownloadProgress(0, 0).fraction)
    }

    @Test fun downloadManagerCleanupMatcherCannotMatchOtherFiles() {
        assertTrue(UpdateFiles.isManagedPackageName("ComfyUIMobile-v0.1.60-release.apk"))
        assertTrue(UpdateFiles.isManagedPackageName("comfyuimobile-V1.2.3-release.APK.part"))
        assertFalse(UpdateFiles.isManagedPackageName("ComfyUIMobile-release.apk"))
        assertFalse(UpdateFiles.isManagedPackageName("other-v0.1.60-release.apk"))
        assertFalse(UpdateFiles.isManagedPackageName("ComfyUIMobile-v0.1.60-release.apk.exe"))
        assertFalse(UpdateFiles.isManagedPackageName("notes.txt"))
    }
}
