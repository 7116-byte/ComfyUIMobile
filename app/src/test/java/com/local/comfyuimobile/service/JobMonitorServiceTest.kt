package com.local.comfyuimobile.service

import android.app.Application
import android.app.Notification
import android.app.Service
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowService

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, shadows = [CountingServiceShadow::class])
class JobMonitorServiceTest {
    private val scheduler = TestCoroutineScheduler()
    private lateinit var controller: ServiceController<JobMonitorService>
    private lateinit var service: JobMonitorService
    private lateinit var shadow: CountingServiceShadow

    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        controller = Robolectric.buildService(JobMonitorService::class.java).create()
        service = controller.get()
        shadow = Shadow.extract(service)
        scheduler.runCurrent()
    }

    @After fun tearDown() {
        controller.destroy()
        Dispatchers.resetMain()
    }

    @Test fun duplicateAndLateSavingProgressStartsAlwaysPromote() {
        assertEquals(1, shadow.promotions)
        val task = Job()
        @Suppress("UNCHECKED_CAST")
        val monitors = field("monitors") as MutableMap<String, Job>
        @Suppress("UNCHECKED_CAST")
        val servers = field("serverUrls") as MutableMap<String, String>
        @Suppress("UNCHECKED_CAST")
        val saving = field("savingJobs") as MutableSet<String>
        monitors["task"] = task
        servers["task"] = "http://server"
        saving.add("task")
        assertTrue(JobMonitorService.isMonitoring("http://server/", "task"))
        service.onStartCommand(Intent(service, JobMonitorService::class.java)
            .setAction(JobMonitorService.ACTION_PROGRESS).putExtra(JobMonitorService.EXTRA_PROMPT_ID, "task"), 0, 1)
        scheduler.runCurrent()
        assertEquals(2, shadow.promotions)
        service.onStartCommand(Intent(service, JobMonitorService::class.java)
            .putExtra(JobMonitorService.EXTRA_PROMPT_ID, "task")
            .putExtra(JobMonitorService.EXTRA_BASE_URL, "http://server"), 0, 2)
        scheduler.runCurrent()
        assertEquals(3, shadow.promotions)
        assertSame(task, monitors["task"])
        task.cancel()
    }

    @Test fun newStartCancelsOldIdleShutdownAndStopsUsingLatestStartId() {
        fun send(id: Int) = service.onStartCommand(Intent(service, JobMonitorService::class.java)
            .setAction(JobMonitorService.ACTION_STOP).putExtra(JobMonitorService.EXTRA_PROMPT_ID, "missing"), 0, id)
        send(1)
        scheduler.runCurrent()
        scheduler.advanceTimeBy(400)
        send(2)
        scheduler.runCurrent()
        scheduler.advanceTimeBy(200)
        scheduler.runCurrent()
        assertFalse(shadow.isStoppedBySelf)
        assertFalse(shadow.isForegroundStopped)
        scheduler.advanceTimeBy(300)
        scheduler.runCurrent()
        assertTrue(shadow.isStoppedBySelf)
        assertEquals(2, shadow.stopSelfResultId)
    }

    private fun field(name: String): Any = JobMonitorService::class.java.getDeclaredField(name)
        .apply { isAccessible = true }.get(service)!!
}

@Implements(Service::class)
class CountingServiceShadow : ShadowService() {
    var promotions = 0
    @Implementation
    override fun startForeground(id: Int, notification: Notification) {
        promotions++
        super.startForeground(id, notification)
    }
}
