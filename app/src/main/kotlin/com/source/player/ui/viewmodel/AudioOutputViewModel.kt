package com.source.player.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.source.player.service.AudioOutputDevice
import com.source.player.service.AudioOutputManager
import com.source.player.service.PlaybackController
import com.source.player.service.SonosManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class AudioOutputViewModel
@Inject
constructor(
        private val audioOutputManager: AudioOutputManager,
        private val sonosManager: SonosManager,
        private val playbackController: PlaybackController,
) : ViewModel() {

    val availableDevices: StateFlow<List<AudioOutputDevice>> = audioOutputManager.availableDevices

    val activeDevice: StateFlow<AudioOutputDevice?> = audioOutputManager.activeDevice

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    fun selectDevice(device: AudioOutputDevice) {
        val routeId = device.routeId
        if (routeId != null && routeId.startsWith("sonos:")) {
            // Route audio to Sonos via UPnP
            val sonosId = routeId.removePrefix("sonos:")
            val sonosDevice =
                    sonosManager.devices.value.firstOrNull { it.id == sonosId } ?: return
            playbackController.activateSonos(sonosDevice)
        } else {
            // If we were on Sonos, deactivate it first
            if (sonosManager.activeDevice.value != null) {
                playbackController.deactivateSonos()
            }
            // Local hardware or MediaRouter (Chromecast handled by CastPlayer listener)
            audioOutputManager.switchTo(device.id)
        }
    }

    // Keep old Int-based overload for any existing callers
    fun selectDevice(deviceId: Int) {
        val device = availableDevices.value.firstOrNull { it.id == deviceId } ?: return
        selectDevice(device)
    }

    fun resetToDefault() {
        if (sonosManager.activeDevice.value != null) {
            playbackController.deactivateSonos()
        }
        audioOutputManager.resetToDefault()
    }

    /** Scan for all devices (local hardware + Sonos). Shows a spinner in the UI. */
    fun scanAll() {
        viewModelScope.launch {
            _isScanning.value = true
            sonosManager.discover()
            // Give SSDP a few seconds to gather responses, then stop spinner
            delay(5_000)
            _isScanning.value = false
        }
    }
}
