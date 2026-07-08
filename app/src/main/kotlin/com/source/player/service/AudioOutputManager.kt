package com.source.player.service

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Represents a single audio output destination — either a local hardware device
 * (from AudioManager) or a remote media route (Sonos, Chromecast, etc. via MediaRouter).
 *
 * [routeId] is non-null only for remote MediaRouter routes — used to look up and select
 * the route when the user picks it.
 */
data class AudioOutputDevice(
        val id: Int,
        val name: String,
        val type: DeviceCategory,
        val rawType: Int,
        val isActive: Boolean = false,
        /** Non-null for remote (MediaRouter) routes. Null for local hardware devices. */
        val routeId: String? = null,
)

/**
 * High-level grouping used in the bottom-sheet UI.
 */
enum class DeviceCategory(val label: String) {
    BUILTIN("Built-in"),
    WIRED("Wired"),
    BLUETOOTH("Bluetooth"),
    WIFI("Wi-Fi"),
    USB("USB"),
    HDMI("HDMI"),
    OTHER("Other"),
}

/**
 * AudioOutputManager — single source of truth for all audio output destinations.
 *
 * Merges two sources:
 * 1. [AudioManager.getDevices] → local hardware (speaker, earpiece, BT, wired, USB, HDMI…)
 * 2. [MediaRouter] → remote network routes (Sonos, Chromecast, Cast-enabled devices…)
 *
 * Real-time updates arrive via [AudioDeviceCallback] and [MediaRouter.Callback].
 * Switching uses [AudioManager.setCommunicationDevice] for local devices (API 31+) and
 * [MediaRouter.selectRoute] for remote routes.
 */
@Singleton
class AudioOutputManager
@Inject
constructor(
        @ApplicationContext private val context: Context,
        private val sonosManager: SonosManager,
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // MediaRouter for remote route discovery (Sonos, Chromecast, etc.)
    private val mediaRouter = MediaRouter.getInstance(context)
    private val routeSelector =
            MediaRouteSelector.Builder()
                    .addControlCategory(androidx.mediarouter.media.MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
                    .addControlCategory(
                            com.google.android.gms.cast.CastMediaControlIntent.categoryForCast(
                                    com.google.android.gms.cast.CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
                            )
                    )
                    .build()

    private val _availableDevices = MutableStateFlow<List<AudioOutputDevice>>(emptyList())
    val availableDevices: StateFlow<List<AudioOutputDevice>> = _availableDevices.asStateFlow()

    private val _activeDevice = MutableStateFlow<AudioOutputDevice?>(null)
    val activeDevice: StateFlow<AudioOutputDevice?> = _activeDevice.asStateFlow()

    private var userSelectedDeviceId: Int? = null

    /**
     * Funnel for refresh triggers. MediaRouter fires onRouteChanged several times per
     * second during volume drags/route chatter, and each rebuild does a cross-process
     * AudioManager.getDevices() call on the main thread — coalesce bursts to one rebuild.
     */
    private val refreshRequests =
            MutableSharedFlow<Unit>(
                    extraBufferCapacity = 1,
                    onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )

    // ---- Callbacks ----

    /** Fires when local hardware devices connect / disconnect. */
    private val audioDeviceCallback =
            object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) {
                    refreshDevices()
                }

                override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) {
                    removed.forEach { info ->
                        if (info.id == userSelectedDeviceId) {
                            userSelectedDeviceId = null
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                audioManager.clearCommunicationDevice()
                            }
                        }
                    }
                    refreshDevices()
                }
            }

    /** Fires when remote routes (Sonos, Cast) appear, disappear, or change. */
    private val mediaRouterCallback =
            object : MediaRouter.Callback() {
                override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
                    refreshDevices()
                }

                override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) {
                    refreshDevices()
                }

                override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
                    refreshDevices()
                }

                override fun onRouteSelected(
                        router: MediaRouter,
                        route: MediaRouter.RouteInfo,
                        reason: Int,
                ) {
                    refreshDevices()
                }

                override fun onRouteUnselected(
                        router: MediaRouter,
                        route: MediaRouter.RouteInfo,
                        reason: Int,
                ) {
                    refreshDevices()
                }
            }

    init {
        // Trailing debounce: collectLatest restarts the delay on every new request,
        // so a callback burst produces exactly one rebuild 250ms after it settles.
        scope.launch {
            refreshRequests.collectLatest {
                delay(250)
                doRefreshDevices()
            }
        }

        audioManager.registerAudioDeviceCallback(audioDeviceCallback, mainHandler)
        mediaRouter.addCallback(
                routeSelector,
                mediaRouterCallback,
                MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY,
        )
        // Start Sonos discovery and reactively update when devices are found
        sonosManager.discover()
        scope.launch {
            sonosManager.devices.collectLatest {
                refreshDevices()
            }
        }
        // Refresh the active indicator when Sonos active device changes
        scope.launch {
            sonosManager.activeDevice.collectLatest {
                refreshDevices()
            }
        }
    }

    // ---- Public API ----

    /**
     * Switch audio output to the device identified by [deviceId].
     * - For local hardware: uses [AudioManager.setCommunicationDevice] (API 31+)
     * - For remote routes (Sonos/Cast): uses [MediaRouter.selectRoute]
     */
    fun switchTo(deviceId: Int) {
        val target = _availableDevices.value.firstOrNull { it.id == deviceId } ?: return

        if (target.routeId != null) {
            // Remote route — find and select via MediaRouter
            val route = mediaRouter.routes.firstOrNull { it.id == target.routeId } ?: return
            mediaRouter.selectRoute(route)
            userSelectedDeviceId = deviceId
        } else {
            // Local hardware — use AudioManager
            val deviceInfo =
                    audioManager
                            .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                            .firstOrNull { it.id == deviceId }
                            ?: return
            userSelectedDeviceId = deviceId
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.setCommunicationDevice(deviceInfo)
            }
        }
        refreshDevices()
    }

    /** Reset to system-default audio routing. */
    fun resetToDefault() {
        userSelectedDeviceId = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        }
        // Unselect any remote route by selecting the default route
        mediaRouter.selectRoute(mediaRouter.defaultRoute)
        refreshDevices()
    }

    // ---- Internal ----

    private fun refreshDevices() {
        refreshRequests.tryEmit(Unit)
    }

    private fun doRefreshDevices() {
        val sonosActive = sonosManager.activeDevice.value != null
        val localDevices = buildLocalDevices(forceInactive = sonosActive)
        val remoteRoutes = buildRemoteRoutes(forceInactive = sonosActive)
        val sonosDevices = buildSonosDevices()
        val all =
                (sonosDevices + localDevices + remoteRoutes)
                        .distinctBy { d ->
                            d.routeId?.takeIf { it.startsWith("sonos:") }
                                    ?: (d.name.lowercase().trim() + ":" + d.type.name)
                        }
                        .sortedBy { it.type.ordinal }

        _availableDevices.value = all
        _activeDevice.value =
                all.firstOrNull { it.isActive }
                        ?: all.firstOrNull { it.type == DeviceCategory.BUILTIN }
    }

    private fun buildSonosDevices(): List<AudioOutputDevice> {
        val offset = 200_000
        val activeSonosId = sonosManager.activeDevice.value?.id
        return sonosManager.devices.value.mapIndexed { index, sonos ->
            AudioOutputDevice(
                    id = offset + index,
                    name = sonos.name,
                    type = DeviceCategory.WIFI,
                    rawType = -1,
                    isActive = sonos.id == activeSonosId,
                    routeId = "sonos:${sonos.id}",
            )
        }
    }

    private fun buildLocalDevices(forceInactive: Boolean = false): List<AudioOutputDevice> {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

        val activeId =
                if (forceInactive) null
                else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    audioManager.communicationDevice?.id ?: userSelectedDeviceId
                } else {
                    userSelectedDeviceId
                }

        return outputs
                .filter { it.isSink }
                .map { info ->
                    AudioOutputDevice(
                            id = info.id,
                            name = friendlyName(info),
                            type = categorize(info.type),
                            rawType = info.type,
                            isActive = activeId != null && info.id == activeId,
                            routeId = null,
                    )
                }
    }

    private fun buildRemoteRoutes(forceInactive: Boolean = false): List<AudioOutputDevice> {
        val selectedRouteId = if (forceInactive) null else mediaRouter.selectedRoute.id
        // Use a large offset to avoid ID collisions with local hardware device IDs
        val idOffset = 100_000

        return mediaRouter.routes
                .filter { route ->
                    // Skip the default (phone) route and local device speaker
                    // — show ALL other routes regardless of category (covers Sonos, Cast, etc.)
                    !route.isDefault && !route.isDeviceSpeaker
                }
                .mapIndexed { index, route ->
                    AudioOutputDevice(
                            id = idOffset + index,
                            name = route.name,
                            type = remoteRouteCategory(route),
                            rawType = -1,
                            isActive = selectedRouteId != null && route.id == selectedRouteId,
                            routeId = route.id,
                    )
                }
    }

    private fun remoteRouteCategory(route: MediaRouter.RouteInfo): DeviceCategory {
        val desc = route.description?.lowercase() ?: ""
        return when {
            desc.contains("cast") || desc.contains("chromecast") -> DeviceCategory.WIFI
            desc.contains("sonos") -> DeviceCategory.WIFI
            desc.contains("bluetooth") -> DeviceCategory.BLUETOOTH
            else -> DeviceCategory.WIFI // remote routes default to Wi-Fi category
        }
    }

    private fun friendlyName(info: AudioDeviceInfo): String {
        val product = info.productName?.toString()?.takeIf { it.isNotBlank() && it != "0" }
        if (product != null) return product
        return when (info.type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Speaker"
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Earpiece"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Headphones"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth (Call)"
            AudioDeviceInfo.TYPE_USB_DEVICE -> "USB Audio"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Headset"
            AudioDeviceInfo.TYPE_IP -> "Wi-Fi Speaker"
            AudioDeviceInfo.TYPE_HDMI -> "HDMI"
            AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI ARC"
            AudioDeviceInfo.TYPE_HDMI_EARC -> "HDMI eARC"
            AudioDeviceInfo.TYPE_DOCK -> "Dock"
            AudioDeviceInfo.TYPE_DOCK_ANALOG -> "Dock (Analog)"
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    when (info.type) {
                        AudioDeviceInfo.TYPE_BLE_HEADSET -> "BLE Headset"
                        AudioDeviceInfo.TYPE_BLE_SPEAKER -> "BLE Speaker"
                        else -> "Audio Device"
                    }
                } else {
                    "Audio Device"
                }
            }
        }
    }

    private fun categorize(type: Int): DeviceCategory =
            when (type) {
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> DeviceCategory.BUILTIN
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> DeviceCategory.WIRED
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> DeviceCategory.BLUETOOTH
                AudioDeviceInfo.TYPE_IP -> DeviceCategory.WIFI
                AudioDeviceInfo.TYPE_USB_DEVICE,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_USB_ACCESSORY -> DeviceCategory.USB
                AudioDeviceInfo.TYPE_HDMI,
                AudioDeviceInfo.TYPE_HDMI_ARC,
                AudioDeviceInfo.TYPE_HDMI_EARC -> DeviceCategory.HDMI
                AudioDeviceInfo.TYPE_DOCK,
                AudioDeviceInfo.TYPE_DOCK_ANALOG -> DeviceCategory.OTHER
                else -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                            (type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                                    type == AudioDeviceInfo.TYPE_BLE_SPEAKER)
                    ) {
                        DeviceCategory.BLUETOOTH
                    } else {
                        DeviceCategory.OTHER
                    }
                }
            }
}
