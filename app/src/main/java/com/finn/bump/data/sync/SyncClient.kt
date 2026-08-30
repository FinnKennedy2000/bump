package com.finn.bump.data.sync

import com.finn.bump.data.AppState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** The server's copy, and the version any write against it must quote. */
data class RemoteState(val version: Long, val state: AppState)

/** The push lost a race: the other phone wrote first. Pull again and retry. */
class VersionConflict : IOException("Server has a newer version")

/**
 * Three calls against the sync backend, over `HttpURLConnection`.
 *
 * That is the JDK's own client and it is enough here: three endpoints, small JSON bodies,
 * no streaming, no interceptors, no auth refresh. OkHttp, Ktor or Retrofit would each add
 * a dependency and a code-generation step to an app whose entire network surface is this
 * file. `HttpURLConnection` is verbose, but the verbosity is all visible in one place.
 */
class SyncClient(private val json: Json) {

    suspend fun createSpace(baseUrl: String): Pairing = withContext(Dispatchers.IO) {
        val body = request(URL("$baseUrl/spaces"), "POST", headers = emptyMap())
        val obj = json.parseToJsonElement(body) as JsonObject
        Pairing(
            baseUrl = baseUrl,
            spaceId = obj.getValue("space_id").jsonPrimitive.content,
            token = obj.getValue("token").jsonPrimitive.content,
        )
    }

    suspend fun pull(p: Pairing): RemoteState = withContext(Dispatchers.IO) {
        val body = request(URL(stateUrl(p)), "GET", authHeader(p))
        val obj = json.parseToJsonElement(body) as JsonObject
        RemoteState(
            version = obj.getValue("version").jsonPrimitive.content.toLong(),
            state = json.decodeFromJsonElement(AppState.serializer(), obj.getValue("state")),
        )
    }

    /** @return the new version. Throws [VersionConflict] if [version] was already stale. */
    suspend fun push(p: Pairing, version: Long, state: AppState): Long =
        withContext(Dispatchers.IO) {
            val body = request(
                url = URL(stateUrl(p)),
                method = "PUT",
                headers = authHeader(p) + ("If-Match" to version.toString()),
                payload = json.encodeToString(AppState.serializer(), state),
            )
            (json.parseToJsonElement(body) as JsonObject)
                .getValue("version").jsonPrimitive.content.toLong()
        }

    private fun stateUrl(p: Pairing) = "${p.baseUrl}/spaces/${p.spaceId}/state"

    private fun authHeader(p: Pairing) = mapOf("Authorization" to "Bearer ${p.token}")

    /**
     * Timeouts are set explicitly because `HttpURLConnection` defaults to none: a phone
     * that drops off wifi mid-request would otherwise hold the coroutine open indefinitely
     * instead of failing and letting the next trigger retry.
     */
    private fun request(
        url: URL,
        method: String,
        headers: Map<String, String>,
        payload: String? = null,
    ): String {
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 15_000
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
            if (payload != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        try {
            payload?.let { conn.outputStream.use { out -> out.write(it.toByteArray()) } }

            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_CONFLICT) throw VersionConflict()
            if (code !in 200..299) {
                // The error body carries the server's `detail`, which is the difference
                // between a status line the user can act on and one they cannot.
                val detail = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IOException("HTTP $code${if (detail.isBlank()) "" else ": $detail"}")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
