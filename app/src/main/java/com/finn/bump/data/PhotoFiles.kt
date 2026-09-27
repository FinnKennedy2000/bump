package com.finn.bump.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * The photo bytes, one file per picture, named by the sha256 of its contents.
 *
 * They live beside the DataStore document rather than inside it: a single photo is larger
 * than the whole of the rest of the app's data, and the document is rewritten on every
 * save. Only the [Photo] metadata goes in `AppState`.
 *
 * A file here is never modified after it is written — a different picture is a different
 * name — which is why sync can treat them as plain copies with nothing to merge.
 *
 * `filesDir/photos` is outside `datastore/`, so the backup rules leave it out of cloud
 * backup (whose 25 MB quota a few dozen photos would exceed, failing the whole backup)
 * and name it explicitly for device-to-device transfer.
 */
class PhotoFiles(context: Context) {

    private val resolver = context.contentResolver
    private val dir = File(context.filesDir, "photos").apply { mkdirs() }

    private val _changes = MutableStateFlow(0)

    /** Ticks whenever a file lands, so a placeholder waiting on a download can redraw. */
    val changes: StateFlow<Int> = _changes

    fun file(blob: String) = File(dir, "$blob.jpg")

    fun stored(): Set<String> =
        dir.list().orEmpty().filter { it.endsWith(".jpg") }.mapTo(HashSet()) { it.removeSuffix(".jpg") }

    fun read(blob: String): ByteArray = file(blob).readBytes()

    /**
     * Downscale, straighten and re-encode a picked image, then store it. Returns its blob.
     *
     * Re-encoding is also what strips the EXIF block, GPS position included — the original
     * file's metadata never reaches this app's storage or the server.
     */
    suspend fun import(uri: Uri): String = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) throw IOException("That picture could not be read")

        val decoded = open(uri).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                inSampleSize = sampleSize(maxOf(bounds.outWidth, bounds.outHeight), MAX_EDGE_PX)
            })
        } ?: throw IOException("That picture could not be read")

        val rotation = open(uri).use { ExifInterface(it).rotationDegrees() }
        val scale = minOf(1f, MAX_EDGE_PX.toFloat() / maxOf(decoded.width, decoded.height))
        val matrix = Matrix().apply { postScale(scale, scale); postRotate(rotation.toFloat()) }
        val upright = if (scale < 1f || rotation != 0) {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        } else decoded
        if (upright !== decoded) decoded.recycle()  // the full-size decode is tens of MB

        val bytes = ByteArrayOutputStream().use {
            // compress() reports failure by returning false, not by throwing. Unchecked, an
            // empty file would be saved under sha256("") and synced as a photo that never shows.
            if (!upright.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) || it.size() == 0) {
                throw IOException("That picture could not be saved")
            }
            it.toByteArray()
        }
        sha256(bytes).also { writeFile(it, bytes) }
    }

    /** Store bytes fetched from the server, refusing any that are not what they claim to be. */
    suspend fun write(blob: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        // A truncated download would otherwise become a permanently broken photo: the file
        // would exist, so no later sync would ever fetch it again.
        if (sha256(bytes) != blob) throw IOException("A photo arrived damaged; will retry")
        writeFile(blob, bytes)
    }

    /**
     * Decode for display, at no more than about twice [maxPx] on the long edge. Null if the
     * file is not on this phone — not uploaded by the partner yet, or lost with a restore
     * that did not include photos — or cannot be shown.
     */
    suspend fun decode(blob: String, maxPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        // The blob comes from the synced document, so it is checked before it becomes a path.
        if (!BLOB.matches(blob)) return@withContext null
        val f = file(blob)
        if (!f.exists()) return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        try {
            BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply {
                inSampleSize = sampleSize(maxOf(bounds.outWidth, bounds.outHeight), maxPx)
            })
        } catch (e: OutOfMemoryError) {
            null  // a placeholder, not a crash, for a full-screen view the heap cannot fit
        }
    }

    /** Temp file then rename, so a crash mid-write cannot leave a half photo under a real name. */
    private fun writeFile(blob: String, bytes: ByteArray) {
        val tmp = File(dir, "$blob.tmp")
        try {
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(file(blob))) throw IOException("Could not save the photo")
        } catch (e: IOException) {
            tmp.delete()
            throw e
        }
        _changes.value++
    }

    private fun open(uri: Uri) =
        resolver.openInputStream(uri) ?: throw IOException("That picture could not be opened")

    private companion object {
        /** Enough for a phone screen at full zoom; lands at a few hundred KB as JPEG. */
        const val MAX_EDGE_PX = 2048
        const val JPEG_QUALITY = 85
        val BLOB = Regex("[0-9a-f]{64}")
    }
}

/** The largest power of two that still leaves [edge] at least [target] pixels. */
internal fun sampleSize(edge: Int, target: Int): Int {
    var sample = 1
    while (edge / (sample * 2) >= target) sample *= 2
    return sample
}

internal fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

private fun ExifInterface.rotationDegrees(): Int =
    when (getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
