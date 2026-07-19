package com.eyeofangra.app

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("settings")

/// Every preference here changes real behaviour. Nothing is stored that nothing reads.
data class Settings(
    val pureBlack: Boolean = false,
    val haptics: Boolean = true,
    val keepScreenOn: Boolean = true,
    val volumeKeyShutter: Boolean = true,
)

object SettingsStore {
    private val PURE_BLACK = booleanPreferencesKey("pure_black")
    private val HAPTICS = booleanPreferencesKey("haptics")
    private val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
    private val VOLUME_SHUTTER = booleanPreferencesKey("volume_shutter")

    fun flow(context: Context): Flow<Settings> = context.store.data.map { p ->
        Settings(
            pureBlack = p[PURE_BLACK] ?: false,
            haptics = p[HAPTICS] ?: true,
            keepScreenOn = p[KEEP_SCREEN_ON] ?: true,
            volumeKeyShutter = p[VOLUME_SHUTTER] ?: true,
        )
    }

    suspend fun setPureBlack(context: Context, value: Boolean) = put(context, PURE_BLACK, value)
    suspend fun setHaptics(context: Context, value: Boolean) = put(context, HAPTICS, value)
    suspend fun setKeepScreenOn(context: Context, value: Boolean) = put(context, KEEP_SCREEN_ON, value)
    suspend fun setVolumeShutter(context: Context, value: Boolean) = put(context, VOLUME_SHUTTER, value)

    private suspend fun put(context: Context, key: Preferences.Key<Boolean>, value: Boolean) {
        context.store.edit { it[key] = value }
    }
}
