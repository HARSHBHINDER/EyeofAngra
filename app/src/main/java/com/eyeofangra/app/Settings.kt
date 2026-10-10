package com.eyeofangra.app

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("settings")

/// Every preference here changes real behaviour. Nothing is stored that nothing reads.
data class Settings(
    val pureBlack: Boolean = false,
    val keepScreenOn: Boolean = true,
    val volumeKeyShutter: Boolean = true,
    val onboardingComplete: Boolean = false,
    /// Off: captures go to this app's private storage ("on board"). On: they go to
    /// [storageUri], the folder the user picked.
    val customStorage: Boolean = false,
    val storageUri: String? = null,
    /// "4K", "1080p" or "720p" — read by CameraEngine each time recording starts.
    val videoQuality: String = "1080p",
)

object SettingsStore {
    private val PURE_BLACK = booleanPreferencesKey("pure_black")
    private val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
    private val VOLUME_SHUTTER = booleanPreferencesKey("volume_shutter")
    private val ONBOARDED = booleanPreferencesKey("onboarding_complete")
    private val CUSTOM_STORAGE = booleanPreferencesKey("custom_storage")
    private val STORAGE_URI = stringPreferencesKey("storage_uri")
    private val VIDEO_QUALITY = stringPreferencesKey("video_quality")

    fun flow(context: Context): Flow<Settings> = context.store.data.map { p ->
        Settings(
            pureBlack = p[PURE_BLACK] ?: false,
            keepScreenOn = p[KEEP_SCREEN_ON] ?: true,
            volumeKeyShutter = p[VOLUME_SHUTTER] ?: true,
            onboardingComplete = p[ONBOARDED] ?: false,
            customStorage = p[CUSTOM_STORAGE] ?: false,
            storageUri = p[STORAGE_URI],
            videoQuality = p[VIDEO_QUALITY] ?: "1080p",
        )
    }

    suspend fun setPureBlack(context: Context, value: Boolean) = put(context, PURE_BLACK, value)
    suspend fun setKeepScreenOn(context: Context, value: Boolean) = put(context, KEEP_SCREEN_ON, value)
    suspend fun setVolumeShutter(context: Context, value: Boolean) = put(context, VOLUME_SHUTTER, value)
    suspend fun setOnboardingComplete(context: Context, value: Boolean) = put(context, ONBOARDED, value)
    suspend fun setCustomStorage(context: Context, value: Boolean) = put(context, CUSTOM_STORAGE, value)

    suspend fun setVideoQuality(context: Context, value: String) {
        context.store.edit { it[VIDEO_QUALITY] = value }
    }

    suspend fun setStorageUri(context: Context, value: String?) {
        context.store.edit { if (value == null) it.remove(STORAGE_URI) else it[STORAGE_URI] = value }
    }

    private suspend fun put(context: Context, key: Preferences.Key<Boolean>, value: Boolean) {
        context.store.edit { it[key] = value }
    }
}
