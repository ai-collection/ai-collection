package com.vault.calc.network

import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.Properties

/**
 * SMB/CIFS client — this is the "map a network drive" protocol used by Windows
 * file shares and most NAS devices. Backed by jcifs-ng (SMB2/SMB3 capable).
 */
class SmbStorage(private val connection: NetworkConnection) : RemoteStorage {

    private val context: CIFSContext

    init {
        val props = Properties().apply {
            setProperty("jcifs.smb.client.enableSMB2", "true")
            setProperty("jcifs.smb.client.disableSMB1", "false")
            setProperty("jcifs.smb.client.responseTimeout", "20000")
            setProperty("jcifs.smb.client.connTimeout", "15000")
        }
        val base: CIFSContext = BaseContext(PropertyConfiguration(props))
        context = if (connection.username.isNotBlank()) {
            base.withCredentials(
                NtlmPasswordAuthenticator(
                    connection.domain.ifBlank { null },
                    connection.username,
                    connection.password
                )
            )
        } else {
            base.withGuestCrentials()
        }
    }

    private fun rootUrl(): String {
        val host = connection.host.trim().trimEnd('/')
        val share = connection.share.trim().trim('/')
        return "smb://$host/$share/"
    }

    private fun urlFor(path: String): String {
        val clean = path.trim().trim('/')
        return if (clean.isEmpty()) rootUrl() else rootUrl() + clean + "/"
    }

    override suspend fun list(path: String): List<RemoteEntry> = withContext(Dispatchers.IO) {
        val dir = SmbFile(urlFor(path), context)
        val base = path.trim().trim('/')
        dir.listFiles().orEmpty().mapNotNull { f ->
            val rawName = f.name.trimEnd('/')
            if (rawName.isEmpty()) return@mapNotNull null
            val childPath = if (base.isEmpty()) rawName else "$base/$rawName"
            RemoteEntry(
                name = rawName,
                path = childPath,
                isDirectory = f.isDirectory,
                size = if (f.isDirectory) 0 else runCatching { f.length() }.getOrDefault(0),
                modified = runCatching { f.lastModified() }.getOrDefault(0L)
            )
        }.sortedWith(compareByDescending<RemoteEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    override suspend fun openInput(path: String): InputStream = withContext(Dispatchers.IO) {
        val clean = path.trim().trim('/')
        val file = SmbFile(rootUrl() + clean, context)
        file.inputStream
    }

    override fun close() {
        runCatching { context.close() }
    }
}
