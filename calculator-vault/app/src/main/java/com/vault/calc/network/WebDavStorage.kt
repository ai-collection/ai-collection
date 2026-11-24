package com.vault.calc.network

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

/**
 * Minimal WebDAV client (listing via PROPFIND, download via GET) built on OkHttp.
 * Works with Nextcloud/ownCloud, Apache mod_dav and most NAS WebDAV endpoints.
 */
class WebDavStorage(private val connection: NetworkConnection) : RemoteStorage {

    private val base: String = connection.host.trim().trimEnd('/')
    private val credential: String? =
        if (connection.username.isNotBlank())
            Credentials.basic(connection.username, connection.password)
        else null

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val propfindBody = """
        <?xml version="1.0" encoding="utf-8"?>
        <d:propfind xmlns:d="DAV:">
          <d:prop>
            <d:resourcetype/>
            <d:getcontentlength/>
            <d:getlastmodified/>
          </d:prop>
        </d:propfind>
    """.trimIndent()

    private fun urlFor(path: String): String {
        val clean = path.trim().trim('/')
        return if (clean.isEmpty()) "$base/" else "$base/$clean"
    }

    private fun basePath(): String = runCatching {
        val u = java.net.URL(base)
        u.path.trimEnd('/')
    }.getOrDefault("")

    override suspend fun list(path: String): List<RemoteEntry> = withContext(Dispatchers.IO) {
        val url = urlFor(path).let { if (it.endsWith("/")) it else "$it/" }
        val builder = Request.Builder()
            .url(url)
            .method("PROPFIND", propfindBody.toRequestBody("application/xml".toMediaType()))
            .header("Depth", "1")
        credential?.let { builder.header("Authorization", it) }

        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) error("WebDAV ${response.code}: ${response.message}")
            val body = response.body?.byteStream() ?: error("Empty WebDAV response")
            parseMultiStatus(body, requestedRelPath = path.trim().trim('/'))
        }
    }

    override suspend fun openInput(path: String): InputStream = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(urlFor(path)).get()
        credential?.let { builder.header("Authorization", it) }
        val response = client.newCall(builder.build()).execute()
        if (!response.isSuccessful) {
            response.close()
            error("WebDAV ${response.code}: ${response.message}")
        }
        response.body?.byteStream() ?: error("Empty WebDAV body")
    }

    override fun close() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    private fun parseMultiStatus(stream: InputStream, requestedRelPath: String): List<RemoteEntry> {
        val results = mutableListOf<RemoteEntry>()
        val rootPath = basePath() // server path of the connection base
        val requestedServerPath = normalize("$rootPath/$requestedRelPath")

        stream.use { input ->
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            parser.setInput(input, null)

            var href: String? = null
            var isDir = false
            var size = 0L
            var modified = 0L
            var inResponse = false

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "response" -> {
                            inResponse = true
                            href = null; isDir = false; size = 0L; modified = 0L
                        }
                        "href" -> if (inResponse) href = parser.nextText().trim()
                        "collection" -> if (inResponse) isDir = true
                        "getcontentlength" ->
                            if (inResponse) size = parser.nextText().trim().toLongOrNull() ?: 0L
                        "getlastmodified" ->
                            if (inResponse) modified = parseHttpDate(parser.nextText().trim())
                    }
                    XmlPullParser.END_TAG -> if (parser.name == "response") {
                        val h = href
                        if (h != null) {
                            val serverPath = normalize(decodeHref(h))
                            // Skip the collection entry that represents the folder itself.
                            if (serverPath != requestedServerPath) {
                                val name = serverPath.trimEnd('/').substringAfterLast('/')
                                if (name.isNotEmpty()) {
                                    val rel = if (requestedRelPath.isEmpty()) name
                                    else "$requestedRelPath/$name"
                                    results.add(RemoteEntry(name, rel, isDir, size, modified))
                                }
                            }
                        }
                        inResponse = false
                    }
                }
                event = parser.next()
            }
        }
        return results.sortedWith(
            compareByDescending<RemoteEntry> { it.isDirectory }.thenBy { it.name.lowercase() }
        )
    }

    private fun decodeHref(href: String): String {
        // href may be absolute (https://host/path) or server-relative (/path).
        val path = runCatching { java.net.URL(href).path }.getOrElse { href }
        return runCatching { URLDecoder.decode(path, "UTF-8") }.getOrDefault(path)
    }

    private fun normalize(path: String): String {
        val collapsed = path.replace(Regex("/+"), "/")
        return collapsed.trimEnd('/')
    }

    private fun parseHttpDate(value: String): Long = runCatching {
        val fmt = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US)
        fmt.parse(value)?.time ?: 0L
    }.getOrDefault(0L)
}
