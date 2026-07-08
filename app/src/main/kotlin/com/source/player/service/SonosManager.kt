package com.source.player.service

import android.content.Context
import android.net.wifi.WifiManager
import android.os.SystemClock
import android.util.Log
import com.source.player.data.preferences.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "SonosManager"

data class SonosDevice(
        val id: String,
        val ip: String,
        val name: String,
        val port: Int = 1400,
)

/**
 * Discovers Sonos speakers on the local network via SSDP multicast and
 * controls them via UPnP AVTransport SOAP over HTTP.
 *
 * No Sonos SDK or partner registration required — uses the open UPnP standard
 * that Sonos has exposed since their first devices.
 */
@Singleton
class SonosManager
@Inject
constructor(
        @ApplicationContext private val context: Context,
        private val localAudioHttpServer: LocalAudioHttpServer,
        private val prefs: AppPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Lazy Ktor client — avoids crash if engine isn't ready at class-init time
    private val http by lazy { HttpClient(Android) { expectSuccess = false } }

    private val _devices = MutableStateFlow<List<SonosDevice>>(emptyList())
    val devices: StateFlow<List<SonosDevice>> = _devices.asStateFlow()

    /** The Sonos device currently being used for playback, or null if inactive. */
    private val _activeDevice = MutableStateFlow<SonosDevice?>(null)
    val activeDevice: StateFlow<SonosDevice?> = _activeDevice.asStateFlow()

    /** Emitted when the active Sonos device finishes playing a track (natural end). */
    private val _trackEnded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val trackEnded: SharedFlow<Unit> = _trackEnded.asSharedFlow()

    /** Emitted with true/false when Sonos transport state changes. */
    private val _sonosPlaying = MutableStateFlow(false)
    val sonosPlaying: StateFlow<Boolean> = _sonosPlaying.asStateFlow()

    /** Current Sonos volume 0..100, or null when unknown / no device active. */
    private val _sonosVolume = MutableStateFlow<Int?>(null)
    val sonosVolume: StateFlow<Int?> = _sonosVolume.asStateFlow()

    private var monitorJob: Job? = null
    /** Monotonic deadline (elapsedRealtime ms); STOPPED events before this are ignored. */
    @Volatile private var suppressStopUntilMs: Long = 0L

    /** Triggers an SSDP scan. Safe to call multiple times. */
    fun discover() {
        scope.launch {
            try {
                _devices.value = runSsdpScan()
                Log.d(TAG, "SSDP scan complete, found ${_devices.value.size} Sonos device(s)")
            } catch (e: Exception) {
                Log.e(TAG, "Sonos discovery failed", e)
            }
        }
    }

    // ---- SSDP ----

    private fun runSsdpScan(): List<SonosDevice> {
        val wm = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wm.createMulticastLock("SourcePlayerSonosLock").apply { acquire() }

        val requestZone =
                "M-SEARCH * HTTP/1.1\r\n" +
                        "HOST: 239.255.255.250:1900\r\n" +
                        "MAN: \"ssdp:discover\"\r\n" +
                        "MX: 3\r\n" +
                        "ST: urn:schemas-upnp-org:device:ZonePlayer:1\r\n\r\n"
                        
        val requestRenderer =
                "M-SEARCH * HTTP/1.1\r\n" +
                        "HOST: 239.255.255.250:1900\r\n" +
                        "MAN: \"ssdp:discover\"\r\n" +
                        "MX: 3\r\n" +
                        "ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n\r\n"

        val found = mutableMapOf<String, SonosDevice>()

        try {
            val group = InetAddress.getByName("239.255.255.250")
            val groupAddr = InetSocketAddress(group, 1900)

            // Bind to any available port first, then join the multicast group
            val socket = MulticastSocket(0).apply {
                reuseAddress = true
                soTimeout = 4_000
            }

            // Use NetworkInterface-aware joinGroup (the single-arg version is
            // deprecated and throws on many Android 12+ devices)
            try {
                socket.joinGroup(groupAddr, null) // null = system picks interface
            } catch (e: Exception) {
                // Fallback: try finding the Wi-Fi interface explicitly
                Log.w(TAG, "joinGroup(addr, null) failed, trying Wi-Fi interface", e)
                try {
                    val wifiIf = NetworkInterface.getNetworkInterfaces()
                            ?.asSequence()
                            ?.filter { it.isUp && !it.isLoopback }
                            ?.firstOrNull { it.name.startsWith("wlan") || it.name.startsWith("wifi") }
                    socket.joinGroup(groupAddr, wifiIf)
                } catch (e2: Exception) {
                    // Both attempts failed — close before rethrowing; the socket is
                    // created outside socket.use so it would otherwise leak.
                    socket.close()
                    throw e2
                }
            }

            socket.use { s ->
                val reqZone = requestZone.toByteArray(Charsets.UTF_8)
                val reqRenderer = requestRenderer.toByteArray(Charsets.UTF_8)
                
                // Blast 3 packets of each to overcome UDP drop on Wi-Fi
                repeat(3) {
                    s.send(DatagramPacket(reqZone, reqZone.size, group, 1900))
                    s.send(DatagramPacket(reqRenderer, reqRenderer.size, group, 1900))
                    java.lang.Thread.sleep(150) // Blocking sleep OK here — runs on IO dispatcher inside socket.use
                }

                val buf = ByteArray(4096)
                val pkt = DatagramPacket(buf, buf.size)
                while (true) {
                    try {
                        s.receive(pkt)
                        val response = String(pkt.data, 0, pkt.length)
                        val ip = pkt.address.hostAddress ?: continue
                        // Must be Sonos — even MediaRenderer responses contain "Sonos" in Server header
                        if (!response.contains("Sonos", ignoreCase = true)) continue
                        
                        if (ip !in found) {
                            val device = fetchDeviceInfo(ip, response) ?: continue
                            found[ip] = device
                            Log.d(TAG, "Found Sonos: ${device.name} at ${device.ip}")
                        }
                    } catch (_: SocketTimeoutException) {
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "SSDP scan error", e)
        } finally {
            lock.release()
        }

        return found.values.toList()
    }

    private fun fetchDeviceInfo(ip: String, ssdpResponse: String): SonosDevice? {
        return try {
            val locationUrl = ssdpResponse.lines()
                    .firstOrNull { it.startsWith("LOCATION", ignoreCase = true) }
                    ?.substringAfter(":")?.trim()
                    ?: "http://$ip:1400/xml/device_description.xml"

            // Blocking fetch on IO dispatcher — this is fine inside scope.launch(IO)
            val xml = java.net.URL(locationUrl).readText(timeout = 3_000)
            val name = Regex("<friendlyName[^>]*>(.*?)</friendlyName>")
                    .find(xml)?.groupValues?.get(1)?.trim() ?: "Sonos"
            val udn = Regex("<UDN>(.*?)</UDN>")
                    .find(xml)?.groupValues?.get(1)?.trim()?.removePrefix("uuid:") ?: ip

            SonosDevice(id = udn, ip = ip, name = name)
        } catch (_: Exception) {
            null
        }
    }

    // ---- UPnP AVTransport ----

    /**
     * Tells the Sonos speaker to stream a local file via our local HTTP server and start playing.
     */
    suspend fun playFile(device: SonosDevice, filePath: String, trackTitle: String) {
        // Suppress spurious STOPPED events during the SetAVTransportURI → Play transition
        // window. 8s covers network latency + Sonos' own buffering time.
        suppressStopUntilMs = SystemClock.elapsedRealtime() + 8_000L
        localAudioHttpServer.start()
        val httpUrl = localAudioHttpServer.getUrl(filePath) ?: return
        val mimeType = mimeTypeFor(filePath)
        val metadata = buildDidl(trackTitle, httpUrl, mimeType)
        sendSoap(device, "SetAVTransportURI",
            "<InstanceID>0</InstanceID>" +
            "<CurrentURI>${httpUrl.xmlEscape()}</CurrentURI>" +
            "<CurrentURIMetaData>${metadata.xmlEscape()}</CurrentURIMetaData>")
        sendSoap(device, "Play", "<InstanceID>0</InstanceID><Speed>1</Speed>")
    }

    suspend fun pause(device: SonosDevice) {
        sendSoap(device, "Pause", "<InstanceID>0</InstanceID>")
    }

    suspend fun resume(device: SonosDevice) {
        sendSoap(device, "Play", "<InstanceID>0</InstanceID><Speed>1</Speed>")
    }

    suspend fun stop(device: SonosDevice) {
        sendSoap(device, "Stop", "<InstanceID>0</InstanceID>")
    }

    // ---- UPnP RenderingControl (volume/mute) ----

    suspend fun getVolume(device: SonosDevice): Int? = runCatching {
        val xml = sendRenderingSoap(device, "GetVolume",
            "<InstanceID>0</InstanceID><Channel>Master</Channel>")
        Regex("<CurrentVolume>(\\d+)</CurrentVolume>")
            .find(xml)?.groupValues?.get(1)?.toIntOrNull()
    }.getOrNull()

    suspend fun setVolume(device: SonosDevice, level: Int) {
        val clamped = level.coerceIn(0, 100)
        runCatching {
            sendRenderingSoap(device, "SetVolume",
                "<InstanceID>0</InstanceID><Channel>Master</Channel><DesiredVolume>$clamped</DesiredVolume>")
            _sonosVolume.value = clamped
        }
    }

    suspend fun adjustVolume(device: SonosDevice, delta: Int) {
        val current = _sonosVolume.value ?: getVolume(device) ?: return
        setVolume(device, current + delta)
    }

    suspend fun setMute(device: SonosDevice, muted: Boolean) {
        runCatching {
            sendRenderingSoap(device, "SetMute",
                "<InstanceID>0</InstanceID><Channel>Master</Channel><DesiredMute>${if (muted) 1 else 0}</DesiredMute>")
        }
    }

    /** Mark a Sonos device as the active playback target and start monitoring it. */
    fun activate(device: SonosDevice) {
        _activeDevice.value = device
        _sonosPlaying.value = false
        scope.launch { prefs.setSonosActiveId(device.id) }
        startMonitoring(device)
    }

    /** Stop monitoring and clear the active Sonos device. */
    fun deactivate() {
        monitorJob?.cancel()
        monitorJob = null
        _activeDevice.value?.let { device ->
            scope.launch { runCatching { stop(device) } }
        }
        _activeDevice.value = null
        _sonosPlaying.value = false
        _sonosVolume.value = null
        scope.launch { prefs.setSonosActiveId(null) }
    }

    /**
     * Silently re-bind to a previously active Sonos device without pushing a new
     * track. Used on app relaunch when the speaker is still playing from a
     * previous session.
     */
    fun reactivateWithoutPlay(device: SonosDevice) {
        _activeDevice.value = device
        startMonitoring(device)
    }

    /**
     * Queries the Sonos device for its current transport state.
     * Returns one of: PLAYING, PAUSED_PLAYBACK, STOPPED, TRANSITIONING, NO_MEDIA_PRESENT
     */
    suspend fun getTransportState(device: SonosDevice): String? {
        return try {
            val response = sendSoapWithResponse(device, "GetTransportInfo",
                "<InstanceID>0</InstanceID>")
            Regex("<CurrentTransportState>(.*?)</CurrentTransportState>")
                .find(response)?.groupValues?.get(1)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get transport state", e)
            null
        }
    }

    data class SonosPositionInfo(
        val trackUri: String?,
        val relTimeMs: Long,
        val durationMs: Long,
    )

    /**
     * Queries the current track URI, elapsed time, and duration from AVTransport.
     * Returns null on network/SOAP failure.
     */
    suspend fun getPositionInfo(device: SonosDevice): SonosPositionInfo? {
        val xml = runCatching {
            sendSoapWithResponse(device, "GetPositionInfo", "<InstanceID>0</InstanceID>")
        }.getOrNull() ?: return null
        fun pick(tag: String) = Regex("<$tag>(.*?)</$tag>").find(xml)?.groupValues?.get(1)
        return SonosPositionInfo(
            trackUri = pick("TrackURI")?.takeIf { it.isNotBlank() },
            relTimeMs = pick("RelTime")?.let { parseHmsToMs(it) } ?: 0L,
            durationMs = pick("TrackDuration")?.let { parseHmsToMs(it) } ?: 0L,
        )
    }

    private fun parseHmsToMs(hms: String): Long {
        val parts = hms.split(":").mapNotNull { it.toIntOrNull() }
        return when (parts.size) {
            3 -> (parts[0] * 3600L + parts[1] * 60L + parts[2]) * 1000L
            2 -> (parts[0] * 60L + parts[1]) * 1000L
            1 -> parts[0] * 1000L
            else -> 0L
        }
    }

    /**
     * Polls the Sonos device every 2 seconds to detect when a track finishes naturally.
     * Emits to [trackEnded] when playback transitions from PLAYING to STOPPED.
     */
    private fun startMonitoring(device: SonosDevice) {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            var wasPlaying = false
            var stoppedStreak = 0
            // Prime the volume so the UI has something to show on first frame.
            _sonosVolume.value = getVolume(device)
            var volumePollTick = 0
            while (true) {
                delay(2_000)
                val state = getTransportState(device)
                // Refresh volume every ~6s (external changes from Sonos app/other).
                if (volumePollTick++ % 3 == 0) {
                    getVolume(device)?.let { _sonosVolume.value = it }
                }
                when (state) {
                    "PLAYING" -> {
                        stoppedStreak = 0
                        wasPlaying = true
                        _sonosPlaying.value = true
                    }
                    "PAUSED_PLAYBACK" -> {
                        stoppedStreak = 0
                        _sonosPlaying.value = false
                    }
                    "TRANSITIONING" -> {
                        // Between tracks — leave wasPlaying alone so we don't lose the
                        // "track ended naturally" signal on the next STOPPED poll.
                    }
                    "STOPPED", "NO_MEDIA_PRESENT" -> {
                        _sonosPlaying.value = false
                        if (wasPlaying) {
                            val now = SystemClock.elapsedRealtime()
                            if (now < suppressStopUntilMs) {
                                // In-flight playFile transition — ignore.
                                stoppedStreak = 0
                            } else {
                                stoppedStreak++
                                // Require two consecutive STOPPED polls before declaring
                                // end-of-track to avoid single-poll glitches during
                                // Sonos' own buffering.
                                if (stoppedStreak >= 2) {
                                    _trackEnded.tryEmit(Unit)
                                    wasPlaying = false
                                    stoppedStreak = 0
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun sendSoap(device: SonosDevice, action: String, bodyContent: String) {
        sendSoapWithResponse(device, action, bodyContent)
    }

    private suspend fun sendSoapWithResponse(
        device: SonosDevice, action: String, bodyContent: String
    ): String = sendUpnpSoap(device, SVC_AV_TRANSPORT, action, bodyContent)

    private suspend fun sendRenderingSoap(
        device: SonosDevice, action: String, bodyContent: String
    ): String = sendUpnpSoap(device, SVC_RENDERING_CONTROL, action, bodyContent)

    private suspend fun sendUpnpSoap(
        device: SonosDevice,
        service: UpnpService,
        action: String,
        bodyContent: String,
    ): String {
        val soap = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
            s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body>
    <u:$action xmlns:u="${service.namespace}">
      $bodyContent
    </u:$action>
  </s:Body>
</s:Envelope>""".trimIndent()

        val response = http.post(
            "http://${device.ip}:${device.port}${service.controlPath}"
        ) {
            headers {
                append("SOAPACTION", "\"${service.namespace}#$action\"")
                append("Content-Type", "text/xml; charset=\"utf-8\"")
            }
            setBody(soap)
        }
        return response.bodyAsText()
    }

    private data class UpnpService(val controlPath: String, val namespace: String)
    private val SVC_AV_TRANSPORT =
        UpnpService("/MediaRenderer/AVTransport/Control",
            "urn:schemas-upnp-org:service:AVTransport:1")
    private val SVC_RENDERING_CONTROL =
        UpnpService("/MediaRenderer/RenderingControl/Control",
            "urn:schemas-upnp-org:service:RenderingControl:1")

    private fun buildDidl(title: String, uri: String, mimeType: String): String =
            """<DIDL-Lite xmlns:dc="http://purl.org/dc/elements/1.1/"
  xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"
  xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/">
  <item id="1" parentID="0" restricted="true">
    <dc:title>$title</dc:title>
    <res protocolInfo="http-get:*:$mimeType:*">$uri</res>
    <upnp:class>object.item.audioItem.musicTrack</upnp:class>
  </item>
</DIDL-Lite>"""

    private fun mimeTypeFor(path: String) = when (path.substringAfterLast('.').lowercase()) {
        "mp3" -> "audio/mpeg"
        "flac" -> "audio/flac"
        "m4a" -> "audio/mp4"
        "aac" -> "audio/aac"
        "ogg", "oga" -> "audio/ogg"
        "wav" -> "audio/wav"
        "opus" -> "audio/opus"
        else -> "audio/mpeg"
    }

    private fun String.xmlEscape() = replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}

private fun java.net.URL.readText(timeout: Int): String {
    val conn = openConnection() as java.net.HttpURLConnection
    conn.connectTimeout = timeout
    conn.readTimeout = timeout
    return conn.inputStream.bufferedReader().readText()
}
