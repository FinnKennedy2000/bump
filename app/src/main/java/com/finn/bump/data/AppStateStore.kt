package com.finn.bump.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/**
 * ponytail: the entire app state is one JSON document rewritten on every save, and
 * there is no way to read a subset of it. At this app's ceiling — a few hundred rows
 * over nine months — that costs nothing measurable, and it buys us no Room, no DAOs,
 * no KSP and no migrations. Move to Room if the data ever reaches thousands of rows
 * or gains a query that shouldn't load everything.
 */
private object AppStateSerializer : Serializer<AppState> {

    private val json = Json {
        ignoreUnknownKeys = true   // tolerate state written by a newer build
        encodeDefaults = true
        prettyPrint = false
    }

    override val defaultValue = AppState()

    override suspend fun readFrom(input: InputStream): AppState =
        try {
            json.decodeFromString(AppState.serializer(), input.readBytes().decodeToString())
        } catch (e: SerializationException) {
            throw CorruptionException("Could not read saved data", e)
        }

    override suspend fun writeTo(t: AppState, output: OutputStream) {
        output.write(json.encodeToString(AppState.serializer(), t).encodeToByteArray())
    }
}

/**
 * DataStore writes atomically (temp file, fsync, rename), so a crash mid-write cannot
 * truncate the file. The corruption handler covers the remaining case — a file that
 * cannot be parsed at all — by starting over rather than leaving the app unlaunchable.
 * That trade loses data, which is why it is the fallback and not the strategy.
 */
fun buildAppStateStore(context: Context): DataStore<AppState> =
    DataStoreFactory.create(
        serializer = AppStateSerializer,
        corruptionHandler = ReplaceFileCorruptionHandler { AppState() },
        produceFile = { context.dataStoreFile("bump_state.json") },
    )
