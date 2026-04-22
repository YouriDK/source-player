package com.source.player.service

import androidx.media3.common.DeviceInfo
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Wraps an ExoPlayer / CastPlayer and re-routes the MediaSession's device-volume
 * surface to Sonos when a Sonos device is active. This makes hardware volume
 * buttons and the Android system volume panel control the Sonos speaker instead
 * of (or in addition to) the phone's STREAM_MUSIC.
 */
class SonosAwarePlayer(
        inner: Player,
        private val sonosManager: SonosManager,
        private val scope: CoroutineScope,
) : ForwardingPlayer(inner) {

    override fun getDeviceInfo(): DeviceInfo =
            if (sonosManager.activeDevice.value != null) REMOTE_DEVICE_INFO
            else super.getDeviceInfo()

    override fun getDeviceVolume(): Int =
            sonosManager.sonosVolume.value ?: super.getDeviceVolume()

    @Deprecated("Deprecated in Player; override kept for completeness.")
    override fun setDeviceVolume(volume: Int) {
        val device = sonosManager.activeDevice.value
        if (device != null) scope.launch { sonosManager.setVolume(device, volume) }
        else @Suppress("DEPRECATION") super.setDeviceVolume(volume)
    }

    override fun setDeviceVolume(volume: Int, flags: Int) {
        val device = sonosManager.activeDevice.value
        if (device != null) scope.launch { sonosManager.setVolume(device, volume) }
        else super.setDeviceVolume(volume, flags)
    }

    @Deprecated("Deprecated in Player; override kept for completeness.")
    override fun increaseDeviceVolume() {
        val device = sonosManager.activeDevice.value
        if (device != null) scope.launch { sonosManager.adjustVolume(device, +4) }
        else @Suppress("DEPRECATION") super.increaseDeviceVolume()
    }

    override fun increaseDeviceVolume(flags: Int) {
        val device = sonosManager.activeDevice.value
        if (device != null) scope.launch { sonosManager.adjustVolume(device, +4) }
        else super.increaseDeviceVolume(flags)
    }

    @Deprecated("Deprecated in Player; override kept for completeness.")
    override fun decreaseDeviceVolume() {
        val device = sonosManager.activeDevice.value
        if (device != null) scope.launch { sonosManager.adjustVolume(device, -4) }
        else @Suppress("DEPRECATION") super.decreaseDeviceVolume()
    }

    override fun decreaseDeviceVolume(flags: Int) {
        val device = sonosManager.activeDevice.value
        if (device != null) scope.launch { sonosManager.adjustVolume(device, -4) }
        else super.decreaseDeviceVolume(flags)
    }

    @Deprecated("Deprecated in Player; override kept for completeness.")
    override fun setDeviceMuted(muted: Boolean) {
        val device = sonosManager.activeDevice.value
        if (device != null) scope.launch { sonosManager.setMute(device, muted) }
        else @Suppress("DEPRECATION") super.setDeviceMuted(muted)
    }

    override fun setDeviceMuted(muted: Boolean, flags: Int) {
        val device = sonosManager.activeDevice.value
        if (device != null) scope.launch { sonosManager.setMute(device, muted) }
        else super.setDeviceMuted(muted, flags)
    }

    override fun isCommandAvailable(command: Int): Boolean =
            when (command) {
                Player.COMMAND_GET_DEVICE_VOLUME,
                Player.COMMAND_SET_DEVICE_VOLUME,
                Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS,
                Player.COMMAND_ADJUST_DEVICE_VOLUME,
                Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS -> true
                else -> super.isCommandAvailable(command)
            }

    companion object {
        private val REMOTE_DEVICE_INFO =
                DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE)
                        .setMaxVolume(100)
                        .build()
    }
}
