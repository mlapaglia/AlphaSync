package com.alphasync.settings

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {
    val cameraSettings: LiveData<SettingsRepository.CameraSettings> = repository.cameraSettingsFlow.asLiveData()

    fun updateCameraName(cameraName: String) {
        viewModelScope.launch {
            repository.updateCameraName(cameraName)
        }
    }

    fun updateCameraAddress(cameraAddress: String) {
        viewModelScope.launch {
            repository.updateCameraAddress(cameraAddress)
        }
    }
}