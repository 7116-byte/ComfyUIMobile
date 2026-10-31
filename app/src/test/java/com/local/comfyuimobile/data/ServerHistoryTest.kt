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
            assertEquals(1234L, restored.profiles.first { it.baseUrl == address }.lastSeen)
            second.rememberServerAddress("http://192.168.10.8:8188")
            assertEquals(2, second.settings.first().profiles.size)
            second.removeServer(address)
            assertFalse(second.settings.first().profiles.any { it.baseUrl == address })
        } finally { secondJob.cancelAndJoin() }
    }
}
