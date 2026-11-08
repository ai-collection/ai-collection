package com.vault.calc.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.vault.calc.crypto.VaultCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Manages the encrypted store of hidden files.
 *
 * Layout (all inside app-private internal storage, invisible to other apps):
 *   filesDir/vault/<id>.blob   -> AES-GCM encrypted file content
 *   filesDir/vault/index.enc   -> AES-GCM encrypted JSON metadata index
 */
class VaultRepository private constructor(private val appContext: Context) {

    private val vaultDir: File = File(appContext.filesDir, "vault").apply { mkdirs() }
    private val indexFile: File = File(vaultDir, "index.enc")
    private val lock = Any()

    companion object {
        @Volatile
        private var instance: VaultRepository? = null

        fun get(context: Context): VaultRepository =
            instance ?: synchronized(this) {
                instance ?: VaultRepository(context.applicationContext).also { instance = it }
            }
    }

    fun blobFile(id: String): File = File(vaultDir, "$id.blob")

    suspend fun loadItems(): List<VaultItem> = withContext(Dispatchers.IO) {
        synchronized(lock) { readIndex() }
            .sortedByDescending { it.addedAt }
    }

    suspend fun importUri(uri: Uri): VaultItem = withContext(Dispatchers.IO) {
        val resolver = appContext.contentResolver
        val (name, size, mime) = queryMetadata(resolver, uri)
        val id = UUID.randomUUID().toString()
        val blob = blobFile(id)
        resolver.openInputStream(uri)?.use { input ->
            VaultCrypto.encrypt(input, blob)
        } ?: error("Cannot open $uri")

        val item = VaultItem(
            id = id,
            displayName = name,
            mimeType = mime,
            size = size,
            addedAt = System.currentTimeMillis()
        )
        synchronized(lock) {
            val items = readIndex().toMutableList()
            items.add(item)
            writeIndex(items)
        }
        item
    }

    suspend fun importBytes(name: String, mime: String, bytes: ByteArray): VaultItem =
        withContext(Dispatchers.IO) {
            val id = UUID.randomUUID().toString()
            VaultCrypto.encrypt(ByteArrayInputStream(bytes), blobFile(id))
            val item = VaultItem(id, name, mime, bytes.size.toLong(), System.currentTimeMillis())
            synchronized(lock) {
                val items = readIndex().toMutableList()
                items.add(item)
                writeIndex(items)
            }
            item
        }

    /** Encrypts an arbitrary input stream (e.g. a network download) into the vault. */
    suspend fun importStream(name: String, mime: String, input: InputStream): VaultItem =
        withContext(Dispatchers.IO) {
            val id = UUID.randomUUID().toString()
            val counting = CountingInputStream(input)
            counting.use { VaultCrypto.encrypt(it, blobFile(id)) }
            val item = VaultItem(id, name, mime, counting.count, System.currentTimeMillis())
            synchronized(lock) {
                val items = readIndex().toMutableList()
                items.add(item)
                writeIndex(items)
            }
            item
        }

    private class CountingInputStream(stream: InputStream) : FilterInputStream(stream) {
        var count: Long = 0L
            private set

        override fun read(): Int {
            val b = super.read()
            if (b >= 0) count++
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = super.read(b, off, len)
            if (n > 0) count += n
            return n
        }
    }

    suspend fun delete(item: VaultItem) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            blobFile(item.id).delete()
            val items = readIndex().toMutableList()
            items.removeAll { it.id == item.id }
            writeIndex(items)
        }
    }

    /** Decrypts [item] into a caller-provided destination stream (e.g. an export target). */
    suspend fun exportTo(item: VaultItem, out: OutputStream) = withContext(Dispatchers.IO) {
        VaultCrypto.decryptTo(blobFile(item.id), out)
    }

    suspend fun decryptBytes(item: VaultItem): ByteArray = withContext(Dispatchers.IO) {
        VaultCrypto.decryptToBytes(blobFile(item.id))
    }

    // --- index persistence -------------------------------------------------

    private fun readIndex(): List<VaultItem> {
        if (!indexFile.exists()) return emptyList()
        return try {
            val json = String(VaultCrypto.decryptToBytes(indexFile), Charsets.UTF_8)
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                VaultItem(
                    id = o.getString("id"),
                    displayName = o.getString("name"),
                    mimeType = o.optString("mime", "application/octet-stream"),
                    size = o.optLong("size", 0),
                    addedAt = o.optLong("addedAt", 0)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun writeIndex(items: List<VaultItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject()
                    .put("id", item.id)
                    .put("name", item.displayName)
                    .put("mime", item.mimeType)
                    .put("size", item.size)
                    .put("addedAt", item.addedAt)
            )
        }
        val bytes = arr.toString().toByteArray(Charsets.UTF_8)
        val tmp = File(vaultDir, "index.tmp")
        VaultCrypto.encrypt(ByteArrayInputStream(bytes), tmp)
        if (!tmp.renameTo(indexFile)) {
            // Fallback if the atomic rename somehow fails.
            tmp.copyTo(indexFile, overwrite = true)
            tmp.delete()
        }
    }

    private fun queryMetadata(resolver: ContentResolver, uri: Uri): Triple<String, Long, String> {
        var name = "file"
        var size = 0L
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0 && !cursor.isNull(nameIdx)) name = cursor.getString(nameIdx)
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
            }
        }
        val mime = resolver.getType(uri) ?: guessMime(name)
        return Triple(name, size, mime)
    }

    private fun guessMime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "mp4" -> "video/mp4"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }
    }
}
