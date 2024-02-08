package com.alphasync.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class SettingsRepository(private val dataStore: DataStore<Preferences>) {
    data class CameraSettings(val cameraName: String, val cameraAddress: String)
    private val associatedCameraAddressKey = stringPreferencesKey("associated_camera_address")
    private val associatedCameraNameKey = stringPreferencesKey("associated_camera")

    private val cameraAddressFlow: Flow<String> = dataStore.data
        .map { preferences ->
            preferences[associatedCameraAddressKey] ?: ""
        }

    private val cameraNameFlow: Flow<String> = dataStore.data
        .map { preferences ->
            preferences[associatedCameraNameKey] ?: ""
        }

    val cameraSettingsFlow: Flow<CameraSettings> = combine(
        cameraNameFlow,
        cameraAddressFlow
    ) { cameraName, cameraAddress ->
        CameraSettings(cameraName, cameraAddress)
    }

    suspend fun updateCameraName(cameraName: String) {
        dataStore.edit { preferences ->
            preferences[associatedCameraNameKey] = cameraName
        }
    }

    suspend fun updateCameraAddress(cameraAddress: String) {
        dataStore.edit { preferences ->
            preferences[associatedCameraAddressKey] = cameraAddress
        }
    }
}