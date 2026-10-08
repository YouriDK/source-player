package com.source.player.data.freebox.api

import android.util.Log
import com.source.player.BuildConfig
import com.source.player.data.freebox.FbxResult
import com.source.player.data.freebox.FreeboxError
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Thin typed wrapper over the read-only subset of the FreeboxOS HTTP API that Source
 * needs (discovery, pairing, login, fs/ls, /dl/). Stateless: callers provide the
 * resolved base URL (e.g. "http://mafreebox.freebox.fr/api/v16") and, where needed,
 * the session token. Session lifecycle lives in FreeboxSessionManager.
 *
 * Uses the dedicated `@Named("freebox")` client (no Logging plugin, so the
 * X-Fbx-App-Auth header is never dumped to logcat). In debug builds failures are
 * logged with the URL and cause (tag SourceFbx); tokens and bodies never are.
 */
@Singleton
class FreeboxApiClient @Inject constructor(
    @Named(FREEBOX_HTTP) private val http: HttpClient,
    private val json: Json,
) {

    /** Unauthenticated discovery; also serves as the LAN reachability probe. */
    suspend fun apiVersion(
        host: String,
        https: Boolean = false,
        timeoutMillis: Long = DISCOVERY_TIMEOUT_MS,
    ): FbxResult<ApiVersionDto> {
        val scheme = if (https) "https" else "http"
        val url = "$scheme://$host/api_version"
        val response = try {
            http.get(url) {
                timeout {
                    requestTimeoutMillis = timeoutMillis
                    connectTimeoutMillis = timeoutMillis
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logTransportFailure(url, e)
            return FbxResult.Err(FreeboxError.Network(e))
        }
        if (!response.status.isSuccess()) {
            logHttpFailure(url, response.status.value, null)
            return FbxResult.Err(FreeboxError.Http(response.status.value))
        }
        return try {
            FbxResult.Ok(json.decodeFromString<ApiVersionDto>(response.bodyAsText()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logTransportFailure(url, e)
            FbxResult.Err(FreeboxError.Network(e))
        }
    }

    suspend fun authorize(base: String, request: AuthorizeRequestDto): FbxResult<AuthorizeResultDto> =
        envelope("$base/login/authorize/") { url ->
            http.post(url) {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(AuthorizeRequestDto.serializer(), request))
            }
        }

    suspend fun trackAuthorization(base: String, trackId: Int): FbxResult<TrackAuthorizationResultDto> =
        envelope("$base/login/authorize/$trackId") { url -> http.get(url) }

    suspend fun loginChallenge(base: String): FbxResult<LoginResultDto> =
        envelope("$base/login/") { url -> http.get(url) }

    suspend fun openSession(base: String, appId: String, password: String): FbxResult<SessionResultDto> =
        envelope("$base/login/session/") { url ->
            http.post(url) {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(SessionRequestDto.serializer(), SessionRequestDto(appId, password)))
            }
        }

    suspend fun ls(
        base: String,
        sessionToken: String,
        pathB64: String,
        onlyFolder: Boolean = false,
        removeHidden: Boolean = false,
        countSubFolder: Boolean = false,
    ): FbxResult<List<FileInfoDto>> {
        val url = "$base/fs/ls/$pathB64"
        val outcome = envelopeNullable<JsonElement>(url) {
            http.get(it) {
                header(X_FBX_APP_AUTH, sessionToken)
                if (onlyFolder) parameter("onlyFolder", 1)
                if (removeHidden) parameter("removeHidden", 1)
                if (countSubFolder) parameter("countSubFolder", 1)
            }
        }
        return when (outcome) {
            is FbxResult.Ok -> try {
                FbxResult.Ok(parseFileList(outcome.value))
            } catch (e: Exception) {
                logTransportFailure(url, e)
                FbxResult.Err(FreeboxError.Network(e))
            }
            is FbxResult.Err -> outcome
        }
    }

    /**
     * fs/ls result shape depends on the firmware generation: the documented v4-era
     * form is a bare array of entries, but API v16 (Freebox Pop) wraps it as
     * {"entries": [...]}. Accept both.
     */
    private fun parseFileList(result: JsonElement?): List<FileInfoDto> = when (result) {
        null, JsonNull -> emptyList()
        is JsonArray -> json.decodeFromJsonElement(fileListSerializer, result)
        is JsonObject -> result["entries"]
            ?.takeIf { it !is JsonNull }
            ?.let { json.decodeFromJsonElement(fileListSerializer, it) }
            ?: emptyList()
        else -> throw SerializationException("Unexpected fs/ls result shape")
    }

    private val fileListSerializer = ListSerializer(FileInfoDto.serializer())

    /** Raw file download via /dl/ into memory; the box honours Range requests on this endpoint. */
    suspend fun download(
        base: String,
        sessionToken: String,
        pathB64: String,
        range: LongRange? = null,
    ): FbxResult<ByteArray> {
        val url = "$base/dl/$pathB64"
        val response = try {
            http.get(url) {
                header(X_FBX_APP_AUTH, sessionToken)
                header(HttpHeaders.AcceptEncoding, "identity")
                if (range != null) header(HttpHeaders.Range, "bytes=${range.first}-${range.last}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logTransportFailure(url, e)
            return FbxResult.Err(FreeboxError.Network(e))
        }
        if (!response.status.isSuccess()) {
            return FbxResult.Err(errorFromBody(url, response))
        }
        return try {
            FbxResult.Ok(response.body())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logTransportFailure(url, e)
            FbxResult.Err(FreeboxError.Network(e))
        }
    }

    /**
     * Opens `/dl/<pathB64>` and hands the live [HttpResponse] to [block] while its
     * body is still open, so a proxy can forward status, headers (Content-Range,
     * Content-Length, Content-Type) and `bodyAsChannel()` without buffering. The
     * response is released when [block] returns. [rangeHeader] is forwarded verbatim
     * (e.g. "bytes=1000-"). No overall request timeout applies (a track may stream
     * for minutes); only connect/socket timeouts do.
     *
     * Transport failures are thrown as-is (IOException & co); HTTP errors are NOT
     * mapped — the caller inspects `response.status` (403 = session expired).
     */
    suspend fun <T> openDownloadStream(
        base: String,
        sessionToken: String,
        pathB64: String,
        rangeHeader: String?,
        block: suspend (HttpResponse) -> T,
    ): T {
        val url = "$base/dl/$pathB64"
        return try {
            http.prepareGet(url) {
                header(X_FBX_APP_AUTH, sessionToken)
                header(HttpHeaders.AcceptEncoding, "identity")
                if (rangeHeader != null) header(HttpHeaders.Range, rangeHeader)
                timeout { requestTimeoutMillis = Long.MAX_VALUE }
            }.execute { response -> block(response) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logTransportFailure(url, e)
            throw e
        }
    }

    /** Maps a non-2xx response: Freebox envelope error_code when present, bare HTTP status otherwise. */
    private suspend fun errorFromBody(url: String, response: HttpResponse): FreeboxError {
        val status = response.status.value
        val parsed = try {
            json.decodeFromString<FreeboxResponse<JsonElement>>(response.bodyAsText())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        val code = parsed?.takeIf { !it.success }?.errorCode
        logHttpFailure(url, status, code)
        return if (code != null) FreeboxError.Api(code, parsed.msg) else FreeboxError.Http(status)
    }

    /** Executes a request and decodes the standard {success, result, error_code, msg} envelope. */
    private suspend inline fun <reified T> envelope(
        url: String,
        crossinline request: suspend (String) -> HttpResponse,
    ): FbxResult<T> = envelopeNullable<T>(url, request).let { outcome ->
        when (outcome) {
            is FbxResult.Ok -> outcome.value
                ?.let { FbxResult.Ok(it) }
                ?: FbxResult.Err(FreeboxError.Api("missing_result"))
            is FbxResult.Err -> outcome
        }
    }

    /** Like [envelope] but tolerates success answers without a result (e.g. empty listings). */
    private suspend inline fun <reified T> envelopeNullable(
        url: String,
        crossinline request: suspend (String) -> HttpResponse,
    ): FbxResult<T?> {
        val response = try {
            request(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logTransportFailure(url, e)
            return FbxResult.Err(FreeboxError.Network(e))
        }
        val body = try {
            response.bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logTransportFailure(url, e)
            return FbxResult.Err(FreeboxError.Network(e))
        }
        val parsed = try {
            json.decodeFromString<FreeboxResponse<T>>(body)
        } catch (e: Exception) {
            // Not a Freebox envelope: report the transport-level failure.
            return if (!response.status.isSuccess()) {
                logHttpFailure(url, response.status.value, null)
                FbxResult.Err(FreeboxError.Http(response.status.value))
            } else {
                logTransportFailure(url, e)
                FbxResult.Err(FreeboxError.Network(e))
            }
        }
        return if (parsed.success) {
            FbxResult.Ok(parsed.result)
        } else {
            logHttpFailure(url, response.status.value, parsed.errorCode)
            FbxResult.Err(FreeboxError.Api(parsed.errorCode ?: "unknown_error", parsed.msg))
        }
    }

    private fun logTransportFailure(url: String, cause: Exception) {
        if (BuildConfig.DEBUG) {
            Log.e(TAG, "FAIL $url -> ${cause.javaClass.simpleName}: ${cause.message}")
        }
    }

    private fun logHttpFailure(url: String, status: Int, errorCode: String?) {
        if (BuildConfig.DEBUG) {
            Log.e(TAG, "FAIL $url -> HTTP $status${errorCode?.let { " error_code=$it" }.orEmpty()}")
        }
    }

    companion object {
        /** Hilt qualifier of the dedicated Freebox HttpClient (see NetworkModule). */
        const val FREEBOX_HTTP = "freebox"
        const val X_FBX_APP_AUTH = "X-Fbx-App-Auth"
        const val DISCOVERY_TIMEOUT_MS = 2_500L
        private const val TAG = "SourceFbx"
    }
}
