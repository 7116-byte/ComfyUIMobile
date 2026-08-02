package com.local.comfyuimobile.service

import android.content.Intent
import com.local.comfyuimobile.model.AppDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JobNotificationNavigationTest {
    @Test fun activityFlagsReuseExistingMainActivity() {
        val flags = JobNotificationNavigation.activityFlags

        assertTrue(flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        assertTrue(flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
    }

    @Test fun runningAndCompletedNotificationsOpenExpectedPages() {
        assertEquals(AppDestination.PARAMETERS, JobNotificationNavigation.destination(completed = false))
        assertEquals(AppDestination.RESULTS, JobNotificationNavigation.destination(completed = true))
    }

    @Test fun differentJobsDoNotSharePendingIntentRequestCode() {
        assertNotEquals(
            JobNotificationNavigation.requestCode("prompt-one"),
            JobNotificationNavigation.requestCode("prompt-two"),
        )
    }

    @Test fun localSaveNotificationDescribesSaveInsteadOfGeneration() {
        assertEquals(
            "本地保存完成，共 4 项",
            JobNotificationNavigation.completionTitle(localSaveRequested = true, savedCount = 4, failed = false),
        )
        assertEquals(
            "本地保存失败",
            JobNotificationNavigation.completionTitle(localSaveRequested = true, savedCount = 0, failed = true),
        )
    }

    @Test fun jobsWithoutLocalSaveKeepGenerationCompletionTitle() {
        assertEquals(
            "生成完成",
            JobNotificationNavigation.completionTitle(localSaveRequested = false, savedCount = 0, failed = false),
        )
    }
}
