package com.local.comfyuimobile.data

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.local.comfyuimobile.model.ServerProfile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ServerHistoryTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun completedGuideSurvivesRestartWithoutChangingConnectionOrSavingRules() = runBlocking {
        val file = folder.newFolder().resolve("guide.preferences_pb")
        val context = RuntimeEnvironment.getApplication()
        val firstJob = SupervisorJob()
        val first = AppPreferences(context, PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + firstJob), produceFile = { file }))
        try {
            assertFalse(first.settings.first().onboardingCompleted)
            first.completeOnboarding() // Finishing and skipping share the same persisted decision.
            first.rememberServerAddress("http://192.168.1.20:8188")
            first.setLocalDraftsEnabled(true)
        } finally { firstJob.cancelAndJoin() }
        val secondJob = SupervisorJob()
        try {
            val second = AppPreferences(context, PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + secondJob), produceFile = { file }))
            val stored = second.settings.first()
            assertTrue(stored.onboardingCompleted)
            assertEquals("http://192.168.1.20:8188", stored.activeServerUrl)
            assertTrue(stored.localDraftsEnabled)
            assertFalse(stored.autoSaveResults)
            assertTrue(stored.cacheOutputRules.isEmpty())
        } finally { secondJob.cancelAndJoin() }
    }

    @Test fun attemptedAndSuccessfulServersSurviveStoreRestartAndOtherSettingsWrites() = runBlocking {
        val file = folder.newFolder().resolve("history.preferences_pb")
        val context = RuntimeEnvironment.getApplication()
        val firstJob = SupervisorJob()
        val first = AppPreferences(context, PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + firstJob), produceFile = { file }))
        val address = "http://192.168.10.7:8188"
        first.rememberServerAddress(address)
        first.saveServer(ServerProfile("server", "工作站", address, 1234, "test"))
        first.rememberServerAddress(address + "/")
        first.rememberServerAddress("http://192.168.10.8:8188") // Attempt recorded without a successful probe.
        first.setLocalDraftsEnabled(false)
        firstJob.cancelAndJoin()

        val secondJob = SupervisorJob()
        try {
            val second = AppPreferences(context, PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + secondJob), produceFile = { file }))
            val restored = second.settings.first()
            assertEquals("http://192.168.10.8:8188", restored.activeServerUrl)
            assertEquals(2, restored.profiles.size)
            assertTrue(restored.onboardingCompleted) // Old installs with history are not forced through the guide.
            assertEquals(1234L, restored.profiles.first { it.baseUrl == address }.lastSeen)
            second.rememberServerAddress("http://192.168.10.8:8188")
            assertEquals(2, second.settings.first().profiles.size)
            second.removeServer(address)
            assertFalse(second.settings.first().profiles.any { it.baseUrl == address })
            second.removeServer("http://192.168.10.8:8188")
            assertTrue(second.settings.first().profiles.isEmpty())
            assertTrue(second.settings.first().onboardingCompleted)
        } finally { secondJob.cancelAndJoin() }
    }
}
