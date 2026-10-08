package com.source.player.service

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.source.player.data.smb.SmbAuthenticationException
import com.source.player.data.smb.SmbClient
import com.source.player.data.smb.SmbReader
import java.io.FileNotFoundException
import java.io.IOException

/**
 * ExoPlayer [DataSource] reading `smb://` URLs through [SmbClient]. All calls happen on
 * ExoPlayer's loader thread, so the blocking SMB I/O is fine here.
 */
@OptIn(UnstableApi::class)
class SmbDataSource(private val client: SmbClient) : BaseDataSource(/* isNetwork = */ true) {

    class Factory(private val client: SmbClient) : DataSource.Factory {
        override fun createDataSource(): DataSource = SmbDataSource(client)
    }

    private var reader: SmbReader? = null
    private var uri: Uri? = null
    private var bytesRemaining: Long = 0
    private var opened = false

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        val r =
                try {
                    client.openReader(dataSpec.uri.toString())
                } catch (e: IOException) {
                    throw mapException(e)
                }
        reader = r
        try {
            if (dataSpec.position > r.length) {
                throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
            }
            r.seek(dataSpec.position)
        } catch (e: DataSourceException) {
            throw e
        } catch (e: IOException) {
            throw mapException(e)
        }
        bytesRemaining =
                if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length
                else r.length - dataSpec.position
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val r = reader ?: return C.RESULT_END_OF_INPUT
        val toRead = minOf(length.toLong(), bytesRemaining).toInt()
        val read =
                try {
                    r.read(buffer, offset, toRead)
                } catch (e: IOException) {
                    throw mapException(e)
                }
        if (read == -1) return C.RESULT_END_OF_INPUT
        bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = uri

    @Throws(IOException::class)
    override fun close() {
        uri = null
        try {
            reader?.close()
        } catch (e: IOException) {
            throw DataSourceException(e, PlaybackException.ERROR_CODE_IO_UNSPECIFIED)
        } finally {
            reader = null
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }

    private fun mapException(e: IOException): DataSourceException {
        if (e is DataSourceException) return e
        val code =
                when (e) {
                    is FileNotFoundException -> PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
                    is SmbAuthenticationException -> PlaybackException.ERROR_CODE_IO_NO_PERMISSION
                    // SmbNotConfiguredException and every other transport error
                    else -> PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                }
        return DataSourceException(e, code)
    }
}

/**
 * Routes `smb://` URIs to [SmbDataSource] and everything else to [defaultFactory].
 * The delegate is chosen lazily in [open] from the URI scheme ([androidx.media3.datasource.DefaultDataSource]
 * only dispatches on its own fixed scheme list).
 */
@OptIn(UnstableApi::class)
class SchemeDispatchingDataSource
private constructor(
        private val smbFactory: DataSource.Factory,
        private val defaultFactory: DataSource.Factory,
) : DataSource {

    class Factory(
            private val smbFactory: DataSource.Factory,
            private val defaultFactory: DataSource.Factory,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
                SchemeDispatchingDataSource(smbFactory, defaultFactory)
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
        val isSmb = dataSpec.uri.scheme.equals("smb", ignoreCase = true)
        val ds = (if (isSmb) smbFactory else defaultFactory).createDataSource()
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
