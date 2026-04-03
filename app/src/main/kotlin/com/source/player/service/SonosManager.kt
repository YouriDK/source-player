package com.source.player.service

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
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

    private var monitorJob: Job? = null
    private var skipNextStopEvent = false

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
                val wifiIf = NetworkInterface.getNetworkInterfaces()
                        ?.asSequence()
                        ?.filter { it.isUp && !it.isLoopback }
                        ?.firstOrNull { it.name.startsWith("wlan") || it.name.startsWith("wifi") }
                socket.joinGroup(groupAddr, wifiIf)
            }

            socket.use { s ->
                val reqZone = requestZone.toByteArray(Charsets.UTF_8)
                val reqRenderer = requestRenderer.toByteArray(Charsets.UTF_8)
                
                // Blast 3 packets of each to overcome UDP drop on Wi-Fi
                repeat(3) {
                    s.send(DatagramPacket(reqZone, reqZone.size, group, 1900))
                    s.send(DatagramPacket(reqRenderer, reqRenderer.size, group, 1900))
                    Thread.sleep(150)
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
        skipNextStopEvent = true
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

    /** Mark a Sonos device as the active playback target and start monitoring it. */
    fun activate(device: SonosDevice) {
        _activeDevice.value = device
        _sonosPlaying.value = false
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

    /**
     * Polls the Sonos device every 2 seconds to detect when a track finishes naturally.
     * Emits to [trackEnded] when playback transitions from PLAYING to STOPPED.
     */
    private fun startMonitoring(device: SonosDevice) {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            var wasPlaying = false
            while (true) {
                delay(2_000)
                val state = getTransportState(device)
                when (state) {
                    "PLAYING" -> {
                        wasPlaying = true
                        _sonosPlaying.value = true
                    }
                    "PAUSED_PLAYBACK" -> {
                        _sonosPlaying.value = false
                    }
                    "STOPPED", "NO_MEDIA_PRESENT" -> {
                        _sonosPlaying.value = false
                        if (wasPlaying) {
                            if (skipNextStopEvent) {
                                skipNextStopEvent = false
                            } else {
                                _trackEnded.tryEmit(Unit)
                            }
                            wasPlaying = false
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
    ): String {
        val soap = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
            s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body>
    <u:$action xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
      $bodyContent
    </u:$action>
  </s:Body>
</s:Envelope>""".trimIndent()

        val response = http.post(
            "http://${device.ip}:${device.port}/MediaRenderer/AVTransport/Control"
        ) {
            headers {
                append("SOAPACTION",
                    "\"urn:schemas-upnp-org:service:AVTransport:1#$action\"")
                append("Content-Type", "text/xml; charset=\"utf-8\"")
            }
            setBody(soap)
        }
        return response.bodyAsText()
    }

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
