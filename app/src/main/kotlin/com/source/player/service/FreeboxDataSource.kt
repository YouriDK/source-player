package com.source.player.service

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import com.source.player.data.freebox.FreeboxAuthException
import com.source.player.data.freebox.FreeboxClient
import com.source.player.data.freebox.FreeboxPaths
import com.source.player.data.freebox.StreamingAccess
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InterruptedIOException
import kotlinx.coroutines.runBlocking

/**
 * ExoPlayer [DataSource] for `fbx://<display path>` uris (Freebox disk, browsed live).
 *
 * The fbx url is resolved at [open] to the box's `/dl/<base64 path>` endpoint plus the
 * `X-Fbx-App-Auth` session header, then streamed by a plain [DefaultHttpDataSource]
 * (Range requests, keep-alive). A 403 means the session expired: it is dropped,
 * re-opened once and the request retried. Runs on ExoPlayer's loader thread, so the
 * blocking session lookup is fine here.
 */
@OptIn(UnstableApi::class)
class FreeboxDataSource(
        private val client: FreeboxClient,
        private val delegate: HttpDataSource,
) : DataSource {

    class Factory(private val client: FreeboxClient) : DataSource.Factory {
        private val httpFactory =
                DefaultHttpDataSource.Factory()
                        .setAllowCrossProtocolRedirects(true)
                        .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
                        // A sleeping Freebox disk takes 5–10 s to answer the first read.
                        .setReadTimeoutMs(READ_TIMEOUT_MS)

        override fun createDataSource(): DataSource =
                FreeboxDataSource(client, httpFactory.createDataSource())
    }

    /** The original fbx:// uri, reported by [getUri] (never leaks the /dl/ url). */
    private var fbxUri: Uri? = null

    override fun addTransferListener(transferListener: TransferListener) {
        delegate.addTransferListener(transferListener)
    }

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        val fbxUrl = dataSpec.uri.toString()
        fbxUri = dataSpec.uri
        return try {
            openWith(dataSpec, fbxUrl, resolveAccess())
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            if (e.responseCode != 403) throw mapHttpError(e)
            // Session expired / revoked on the box: log in again and retry once.
            delegate.close()
            runBlockingIo { client.dropSession() }
            try {
                openWith(dataSpec, fbxUrl, resolveAccess())
            } catch (retry: HttpDataSource.InvalidResponseCodeException) {
                throw mapHttpError(retry)
            }
        }
    }

    private fun openWith(dataSpec: DataSpec, fbxUrl: String, access: StreamingAccess): Long {
        val spec =
                dataSpec.buildUpon()
                        .setUri(access.downloadUrl(fbxUrl))
                        .setHttpRequestHeaders(
                                dataSpec.httpRequestHeaders +
                                        (StreamingAccess.AUTH_HEADER to access.sessionToken)
                        )
                        .build()
        return delegate.open(spec)
    }

    private fun resolveAccess(): StreamingAccess =
            try {
                runBlockingIo { client.streamingAccess() }
            } catch (e: DataSourceException) {
                throw e
            } catch (e: FreeboxAuthException) {
                throw DataSourceException(e, PlaybackException.ERROR_CODE_IO_NO_PERMISSION)
            } catch (e: FileNotFoundException) {
                throw DataSourceException(e, PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)
            } catch (e: IOException) {
                // FreeboxUnreachableException and every other transport failure.
                throw DataSourceException(e, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
            }

    private fun mapHttpError(e: HttpDataSource.InvalidResponseCodeException): IOException =
            when (e.responseCode) {
                403 -> DataSourceException(e, PlaybackException.ERROR_CODE_IO_NO_PERMISSION)
                404 -> DataSourceException(e, PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)
                else -> e
            }

    /** runBlocking on the loader thread; a cancelled load interrupts it. */
    private fun <T> runBlockingIo(block: suspend () -> T): T =
            try {
                runBlocking { block() }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw InterruptedIOException("Freebox open interrupted")
            }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            delegate.read(buffer, offset, length)

    override fun getUri(): Uri? = if (delegate.uri != null) fbxUri else null

    override fun getResponseHeaders(): Map<String, List<String>> = delegate.responseHeaders

    @Throws(IOException::class)
    override fun close() {
        fbxUri = null
        delegate.close()
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 30_000
    }
}

/**
 * Routes `fbx://` uris to [FreeboxDataSource] and everything else (file://, content://,
 * http…) to [DefaultDataSource]. The delegate is chosen lazily in [open] from the uri
 * scheme, since [DefaultDataSource] only dispatches on its own fixed scheme list.
 */
@OptIn(UnstableApi::class)
class SchemeDispatchingDataSource
private constructor(
        private val freeboxFactory: DataSource.Factory,
        private val defaultFactory: DataSource.Factory,
) : DataSource {

    class Factory(
            private val freeboxFactory: DataSource.Factory,
            private val defaultFactory: DataSource.Factory,
    ) : DataSource.Factory {
        constructor(context: Context, client: FreeboxClient) :
                this(FreeboxDataSource.Factory(client), DefaultDataSource.Factory(context))

        override fun createDataSource(): DataSource =
                SchemeDispatchingDataSource(freeboxFactory, defaultFactory)
    }

    private val transferListeners = mutableListOf<TransferListener>()
    private var delegate: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        transferListeners += transferListener
        delegate?.addTransferListener(transferListener)
    }

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        check(delegate == null) { "DataSource already open" }
        val isFreebox = FreeboxPaths.isFreeboxUrl(dataSpec.uri.toString())
        val ds = (if (isFreebox) freeboxFactory else defaultFactory).createDataSource()
        transferListeners.forEach { ds.addTransferListener(it) }
        delegate = ds
        return ds.open(dataSpec)
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            checkNotNull(delegate).read(buffer, offset, length)

    override fun getUri(): Uri? = delegate?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
            delegate?.responseHeaders ?: emptyMap()

    @Throws(IOException::class)
    override fun close() {
        val ds = delegate ?: return
        delegate = null
        ds.close()
    }
}
