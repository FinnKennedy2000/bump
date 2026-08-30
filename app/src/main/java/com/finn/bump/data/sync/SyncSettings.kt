package com.finn.bump.data.sync

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Which space this phone is paired to, and the token that proves it.
 *
 * Deliberately a separate store from `AppState`: the token is a credential, and `AppState`
 * is the document that gets uploaded. Keeping them apart means the token cannot be
 * pushed to the server by accident, however the sync code is later rearranged.
 */
private val Context.syncDataStore: DataStore<Preferences> by preferencesDataStore("bump_sync")

private val KEY_BASE_URL = stringPreferencesKey("base_url")
private val KEY_SPACE_ID = stringPreferencesKey("space_id")
private val KEY_TOKEN = stringPreferencesKey("token")

/** The deployed backend. Editable in the pairing sheet so a redeploy is not a rebuild. */
const val DEFAULT_BASE_URL = "https://bump-api-production-3acf.up.railway.app"

/**
 * A paired space. Absent means sync is off, and while it is absent the app makes no
 * network request of any kind — which is what keeps "offline unless you opt in" true
 * rather than merely intended.
 */
data class Pairing(val baseUrl: String, val spaceId: String, val token: String) {

    /**
     * One string to send your partner over WhatsApp.
     *
     * Base64 rather than three fields to copy: a single blob is one paste and cannot be
     * assembled wrong. It is encoding, not encryption — the token inside is a live
     * credential, so it belongs in a private message, not a screenshot in a group chat.
     */
    fun toCode(): String =
        Base64.encodeToString("$baseUrl|$spaceId|$token".toByteArray(), Base64.NO_WRAP)

    companion object {
        fun fromCode(code: String): Pairing? = runCatching {
            val parts = String(Base64.decode(code.trim(), Base64.DEFAULT)).split("|")
            if (parts.size != 3 || parts.any { it.isBlank() }) return null
            Pairing(parts[0], parts[1], parts[2])
        }.getOrNull()
    }
}

class SyncSettings(private val context: Context) {

    val pairing: Flow<Pairing?> = context.syncDataStore.data.map { prefs ->
        val id = prefs[KEY_SPACE_ID] ?: return@map null
        val token = prefs[KEY_TOKEN] ?: return@map null
        Pairing(prefs[KEY_BASE_URL] ?: DEFAULT_BASE_URL, id, token)
    }

    suspend fun save(pairing: Pairing) {
        context.syncDataStore.edit {
            it[KEY_BASE_URL] = pairing.baseUrl
            it[KEY_SPACE_ID] = pairing.spaceId
            it[KEY_TOKEN] = pairing.token
        }
    }

    /** Unpair. The local document is untouched — leaving sync must never cost you data. */
    suspend fun clear() {
        context.syncDataStore.edit {
            it.remove(KEY_BASE_URL)
            it.remove(KEY_SPACE_ID)
            it.remove(KEY_TOKEN)
        }
    }
}
