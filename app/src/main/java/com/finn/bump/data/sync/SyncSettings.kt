package com.finn.bump.data.sync

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
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
private val KEY_VOTER = stringPreferencesKey("voter_id")
private val KEY_NAMES_SEEN = longPreferencesKey("names_seen_at")

/** The deployed backend. Editable under Advanced in Settings so a redeploy is not a rebuild. */
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

    /**
     * The server a new space will be created on. Kept across unpairing: the address is a
     * property of the deployment, not of any one pairing, and re-typing it after leaving
     * a space would be a small punishment for nothing.
     */
    val baseUrl: Flow<String> =
        context.syncDataStore.data.map { it[KEY_BASE_URL] ?: DEFAULT_BASE_URL }

    suspend fun setBaseUrl(url: String) {
        context.syncDataStore.edit { it[KEY_BASE_URL] = url }
    }

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

    /**
     * This install's own id, made once, used to tell its name votes from the partner's.
     * Kept here rather than in `AppState` because the record is shared and this must not
     * be: two phones syncing one id would see each other's votes as their own. Survives
     * unpairing, so votes stay yours.
     */
    suspend fun voterId(): String {
        var id: String? = null
        context.syncDataStore.edit { prefs ->
            id = prefs[KEY_VOTER] ?: java.util.UUID.randomUUID().toString().also { prefs[KEY_VOTER] = it }
        }
        return id!!
    }

    /** The newest partner name vote this phone has already told you about, or shown you. */
    val namesSeenAt: Flow<Long> = context.syncDataStore.data.map { it[KEY_NAMES_SEEN] ?: 0L }

    suspend fun setNamesSeenAt(at: Long) {
        context.syncDataStore.edit { prefs ->
            // Only ever forward: a slow write must not re-announce what was already seen.
            if (at > (prefs[KEY_NAMES_SEEN] ?: 0L)) prefs[KEY_NAMES_SEEN] = at
        }
    }

    /** Unpair. The local document is untouched — leaving sync must never cost you data. */
    suspend fun clear() {
        context.syncDataStore.edit {
            it.remove(KEY_SPACE_ID)
            it.remove(KEY_TOKEN)
        }
    }
}
