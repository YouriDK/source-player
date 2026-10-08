package com.source.player.data.freebox.auth

import com.source.player.data.freebox.EndpointResolver
import com.source.player.data.freebox.FbxResult
import com.source.player.data.freebox.FreeboxError
import com.source.player.data.freebox.api.AuthorizeRequestDto
import com.source.player.data.freebox.api.FreeboxApiClient
import com.source.player.data.freebox.api.TrackAuthorizationResultDto
import com.source.player.data.preferences.AppPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * One-time pairing: authorize on the box (physical validation on its display),
 * poll the track until granted, then persist the app_token. Must run on the LAN —
 * the API answers denied_from_external_ip otherwise, which is surfaced as-is.
 * The flow does blocking I/O (encrypted prefs): collect it off the main thread
 * (FreeboxClient.pair applies flowOn(IO)).
 */
class FreeboxPairingManager @Inject constructor(
    private val api: FreeboxApiClient,
    private val tokenStore: AppTokenStore,
    private val prefs: AppPreferences,
    private val resolver: EndpointResolver,
) {

    sealed interface PairingEvent {
        data object Discovering : PairingEvent
        data object AwaitingValidation : PairingEvent
        data object Granted : PairingEvent
        data class Failed(val error: FreeboxError) : PairingEvent
    }

    fun pair(manualHost: String?): Flow<PairingEvent> = flow {
        emit(PairingEvent.Discovering)
        prefs.saveFreeboxManualHost(manualHost)

        val endpoint = when (val resolved = resolver.resolve(forceProbe = true)) {
            is FbxResult.Ok -> resolved.value
            is FbxResult.Err -> {
                emit(PairingEvent.Failed(resolved.error))
                return@flow
            }
        }

        val request = AuthorizeRequestDto(
            appId = FreeboxAppIdentity.APP_ID,
            appName = FreeboxAppIdentity.APP_NAME,
            appVersion = FreeboxAppIdentity.APP_VERSION,
            deviceName = FreeboxAppIdentity.DEVICE_NAME,
        )
        val authorization = when (val outcome = api.authorize(endpoint.base, request)) {
            is FbxResult.Ok -> outcome.value
            is FbxResult.Err -> {
                emit(PairingEvent.Failed(outcome.error))
                return@flow
            }
        }

        emit(PairingEvent.AwaitingValidation)
        while (true) {
            delay(POLL_INTERVAL_MS)
            val status = when (val outcome = api.trackAuthorization(endpoint.base, authorization.trackId)) {
                is FbxResult.Ok -> outcome.value.status
                is FbxResult.Err -> {
                    emit(PairingEvent.Failed(outcome.error))
                    return@flow
                }
            }
            when (status) {
                TrackAuthorizationResultDto.STATUS_PENDING -> continue
                TrackAuthorizationResultDto.STATUS_GRANTED -> {
                    tokenStore.appToken = authorization.appToken
                    emit(PairingEvent.Granted)
                    return@flow
                }
                TrackAuthorizationResultDto.STATUS_DENIED -> {
                    emit(PairingEvent.Failed(FreeboxError.PairingDenied))
                    return@flow
                }
                TrackAuthorizationResultDto.STATUS_TIMEOUT -> {
                    emit(PairingEvent.Failed(FreeboxError.PairingTimeout))
                    return@flow
                }
                else -> {
                    emit(PairingEvent.Failed(FreeboxError.Api(code = "authorize_$status")))
                    return@flow
                }
            }
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 1_000L
    }
}
