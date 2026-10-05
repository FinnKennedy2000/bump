package com.finn.bump.data

import kotlinx.serialization.json.Json
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private val exportJson = Json { prettyPrint = true; encodeDefaults = true }

/**
 * The whole record as one zip: `record.json` plus every live photo as `photos/<blob>.jpg`.
 *
 * It exists so the pregnancy outlives this app, these phones and the sync server — the
 * zip opens anywhere, and the JSON is the same document the app reads, so it could also be
 * put back. Tombstones are left out; nobody keeping a record wants the things they deleted.
 *
 * Returns the blobs that could not be included because their bytes are not on this
 * phone yet (a photo the partner took that has not synced down), so the caller can say
 * so rather than hand over a quietly incomplete archive.
 */
fun writeExport(state: AppState, photoFile: (String) -> File, out: OutputStream): List<String> {
    val live = state.withoutTombstones()
    val missing = mutableListOf<String>()
    ZipOutputStream(out).use { zip ->
        zip.putNextEntry(ZipEntry("record.json"))
        zip.write(exportJson.encodeToString(AppState.serializer(), live).encodeToByteArray())
        zip.closeEntry()
        for (blob in live.photos.map { it.blob }.distinct()) {
            val file = photoFile(blob)
            if (!file.exists()) {
                missing += blob
                continue
            }
            zip.putNextEntry(ZipEntry("photos/$blob.jpg"))
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }
    return missing
}
