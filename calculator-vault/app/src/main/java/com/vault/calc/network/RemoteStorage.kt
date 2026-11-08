package com.vault.calc.network

import java.io.InputStream

/** A file or folder on a remote share. [path] is relative to the connection root. */
data class RemoteEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val modified: Long
)

/**
 * Common interface over the supported network-storage protocols so the UI does
 * not care whether it is talking to SMB or WebDAV.
 */
interface RemoteStorage {
    /** Lists the children of [path] ("" or "/" means the connection root). */
    suspend fun list(path: String): List<RemoteEntry>

    /** Opens a stream to download the file at [path]. Caller closes it. */
    suspend fun openInput(path: String): InputStream

    fun close()
}

enum class RemoteType { SMB, WEBDAV }

data class NetworkConnection(
    val id: String,
    val name: String,
    val type: RemoteType,
    /** SMB: server host or IP. WebDAV: full base URL, e.g. https://host/remote.php/dav */
    val host: String,
    /** SMB only: the share name, e.g. "Public". Ignored for WebDAV. */
    val share: String = "",
    val username: String = "",
    val password: String = "",
    /** SMB only: optional Windows domain / workgroup. */
    val domain: String = ""
)
