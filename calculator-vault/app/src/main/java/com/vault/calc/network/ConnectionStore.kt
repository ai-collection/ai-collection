package com.vault.calc.network

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Persists saved network connections (including credentials) encrypted at rest. */
class ConnectionStore private constructor(private val prefs: SharedPreferences) {

    companion object {
        private const val FILE_NAME = "vault_connections"
        private const val KEY = "connections"

        @Volatile
        private var instance: ConnectionStore? = null

        fun get(context: Context): ConnectionStore =
            instance ?: synchronized(this) {
                instance ?: create(context.applicationContext).also { instance = it }
            }

        private fun create(context: Context): ConnectionStore {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = EncryptedSharedPreferences.create(
                context,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            return ConnectionStore(prefs)
        }

        fun createClient(connection: NetworkConnection): RemoteStorage = when (connection.type) {
            RemoteType.SMB -> SmbStorage(connection)
            RemoteType.WEBDAV -> WebDavStorage(connection)
        }
    }

    fun all(): List<NetworkConnection> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            NetworkConnection(
                id = o.getString("id"),
                name = o.getString("name"),
                type = RemoteType.valueOf(o.getString("type")),
                host = o.getString("host"),
                share = o.optString("share", ""),
                username = o.optString("username", ""),
                password = o.optString("password", ""),
                domain = o.optString("domain", "")
            )
        }
    }

    fun save(connection: NetworkConnection): NetworkConnection {
        val withId = if (connection.id.isBlank())
            connection.copy(id = UUID.randomUUID().toString()) else connection
        val list = all().toMutableList()
        val idx = list.indexOfFirst { it.id == withId.id }
        if (idx >= 0) list[idx] = withId else list.add(withId)
        persist(list)
        return withId
    }

    fun delete(id: String) {
        persist(all().filterNot { it.id == id })
    }

    private fun persist(list: List<NetworkConnection>) {
        val arr = JSONArray()
        list.forEach { c ->
            arr.put(
                JSONObject()
                    .put("id", c.id)
                    .put("name", c.name)
                    .put("type", c.type.name)
                    .put("host", c.host)
                    .put("share", c.share)
                    .put("username", c.username)
                    .put("password", c.password)
                    .put("domain", c.domain)
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }
}
