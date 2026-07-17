package com.local.comfyuimobile.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.local.comfyuimobile.model.ServerProfile
import com.local.comfyuimobile.model.CacheOutputRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "comfy_mobile")

data class StoredSettings(
    val profiles: List<ServerProfile> = emptyList(),
    val activeServerUrl: String = "",
    val promptHistory: List<String> = emptyList(),
    val submittedJobs: Set<String> = emptySet(),
    val autoSaveResults: Boolean = false,
    val lastUpdateCheck: Long = 0L,
    val recentWorkflow: String = "",
    val cacheOutputRules: List<CacheOutputRule> = emptyList(),
    val cacheClearedAt: Long = 0L,
    val favoriteResultKeys: Set<String> = emptySet(),
)

class AppPreferences(private val context: Context) {
    private object Keys {
        val profiles = stringPreferencesKey("profiles")
        val activeServerUrl = stringPreferencesKey("active_server_url")
        val promptHistory = stringPreferencesKey("prompt_history")
        val submittedJobs = stringPreferencesKey("submitted_jobs")
        val autoSaveResults = booleanPreferencesKey("auto_save_results")
        val lastUpdateCheck = longPreferencesKey("last_update_check")
        val recentWorkflow = stringPreferencesKey("recent_workflow")
        val cacheOutputRules = stringPreferencesKey("cache_output_rules")
        val cacheClearedAt = longPreferencesKey("cache_cleared_at")
        val favoriteResultKeys = stringPreferencesKey("favorite_result_keys")
    }

    val settings: Flow<StoredSettings> = context.dataStore.data.map { preferences ->
        StoredSettings(
            profiles = decodeProfiles(preferences[Keys.profiles].orEmpty()),
            activeServerUrl = preferences[Keys.activeServerUrl].orEmpty(),
            promptHistory = decodeStrings(preferences[Keys.promptHistory].orEmpty()).take(PromptHistory.MAX_SIZE),
            submittedJobs = decodeStrings(preferences[Keys.submittedJobs].orEmpty()).toSet(),
            autoSaveResults = preferences[Keys.autoSaveResults] ?: false,
            lastUpdateCheck = preferences[Keys.lastUpdateCheck] ?: 0L,
            recentWorkflow = preferences[Keys.recentWorkflow].orEmpty(),
            cacheOutputRules = decodeCacheOutputRules(preferences[Keys.cacheOutputRules].orEmpty()),
            cacheClearedAt = preferences[Keys.cacheClearedAt] ?: 0L,
            favoriteResultKeys = decodeStrings(preferences[Keys.favoriteResultKeys].orEmpty()).toSet(),
        )
    }

    suspend fun saveServer(profile: ServerProfile) {
        context.dataStore.edit { preferences ->
            val current = decodeProfiles(preferences[Keys.profiles].orEmpty())
            val merged = listOf(profile) + current.filterNot { it.baseUrl == profile.baseUrl }
            preferences[Keys.profiles] = encodeProfiles(merged.take(12))
            preferences[Keys.activeServerUrl] = profile.baseUrl
        }
    }

    suspend fun removeServer(baseUrl: String) {
        context.dataStore.edit { preferences ->
            val remaining = decodeProfiles(preferences[Keys.profiles].orEmpty()).filterNot { it.baseUrl == baseUrl }
            preferences[Keys.profiles] = encodeProfiles(remaining)
            if (preferences[Keys.activeServerUrl] == baseUrl) preferences.remove(Keys.activeServerUrl)
        }
    }

    suspend fun savePromptHistory(history: List<String>) {
        context.dataStore.edit { it[Keys.promptHistory] = encodeStrings(history.take(PromptHistory.MAX_SIZE)) }
    }

    suspend fun saveSubmittedJobs(ids: Set<String>) {
        context.dataStore.edit { it[Keys.submittedJobs] = encodeStrings(ids.toList().takeLast(200)) }
    }

    suspend fun setAutoSaveResults(enabled: Boolean) {
        context.dataStore.edit { it[Keys.autoSaveResults] = enabled }
    }

    suspend fun setLastUpdateCheck(timestamp: Long) {
        context.dataStore.edit { it[Keys.lastUpdateCheck] = timestamp }
    }

    suspend fun setRecentWorkflow(path: String) {
        context.dataStore.edit { it[Keys.recentWorkflow] = path }
    }

    suspend fun saveCacheOutputRules(rules: List<CacheOutputRule>) {
        context.dataStore.edit { preferences ->
            preferences[Keys.cacheOutputRules] = JSONArray().apply {
                rules.forEach { rule ->
                    put(
                        JSONObject()
                            .put("serverUrl", rule.serverUrl)
                            .put("workflowPath", rule.workflowPath)
                            .put("workflowName", rule.workflowName)
                            .put("nodeId", rule.nodeId)
                            .put("nodeTitle", rule.nodeTitle)
                            .put("nodeType", rule.nodeType)
                            .put("enabled", rule.enabled),
                    )
                }
            }.toString()
        }
    }

    suspend fun setCacheClearedAt(timestamp: Long) {
        context.dataStore.edit { it[Keys.cacheClearedAt] = timestamp }
    }

    suspend fun saveFavoriteResultKeys(keys: Set<String>) {
        context.dataStore.edit { it[Keys.favoriteResultKeys] = encodeStrings(keys.take(1_000)) }
    }

    private fun decodeProfiles(raw: String): List<ServerProfile> = runCatching {
        val array = JSONArray(raw.ifBlank { "[]" })
        buildList {
            repeat(array.length()) { index ->
                val item = array.getJSONObject(index)
                add(
                    ServerProfile(
                        id = item.optString("id"),
                        name = item.optString("name"),
                        baseUrl = item.getString("baseUrl"),
                        lastSeen = item.optLong("lastSeen"),
                        comfyVersion = item.optString("comfyVersion"),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun encodeProfiles(profiles: List<ServerProfile>): String = JSONArray().apply {
        profiles.forEach { profile ->
            put(
                JSONObject()
                    .put("id", profile.id)
                    .put("name", profile.name)
                    .put("baseUrl", profile.baseUrl)
                    .put("lastSeen", profile.lastSeen)
                    .put("comfyVersion", profile.comfyVersion),
            )
        }
    }.toString()

    private fun decodeStrings(raw: String): List<String> = runCatching {
        val array = JSONArray(raw.ifBlank { "[]" })
        List(array.length()) { array.optString(it) }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())

    private fun encodeStrings(values: Collection<String>): String = JSONArray(values).toString()

    private fun decodeCacheOutputRules(raw: String): List<CacheOutputRule> = runCatching {
        val array = JSONArray(raw.ifBlank { "[]" })
        List(array.length()) { index ->
            array.getJSONObject(index).let { item ->
                CacheOutputRule(
                    serverUrl = item.optString("serverUrl"),
                    workflowPath = item.optString("workflowPath"),
                    workflowName = item.optString("workflowName"),
                    nodeId = item.optString("nodeId"),
                    nodeTitle = item.optString("nodeTitle"),
                    nodeType = item.optString("nodeType"),
                    enabled = item.optBoolean("enabled", true),
                )
            }
        }.filter { it.serverUrl.isNotBlank() && it.workflowPath.isNotBlank() && it.nodeId.isNotBlank() }
    }.getOrDefault(emptyList())
}
