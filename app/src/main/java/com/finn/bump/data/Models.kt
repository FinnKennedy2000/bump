package com.finn.bump.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDate
import java.util.UUID

/** ISO-8601 (`2026-08-29`). Readable in the raw JSON, which matters when debugging by eye. */
object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

typealias Iso8601Date = @Serializable(with = LocalDateSerializer::class) LocalDate

private fun newId() = UUID.randomUUID().toString()

/**
 * What you know about the sex, including the two ways of not knowing.
 *
 * [SURPRISE] is deliberately distinct from [UNKNOWN]: one is a decision, the other is
 * a gap. Collapsing them would make the app keep asking a question already answered.
 */
@Serializable
enum class BabySex { UNKNOWN, GIRL, BOY, SURPRISE }

/**
 * Every logged item carries a stable [id], an [updatedAt] stamp and a [deleted] tombstone.
 *
 * [id] gives stable list keys and makes edit/delete/undo unambiguous; [updatedAt] orders
 * edits. Together they are what sync merges on.
 *
 * [deleted] exists because sync cannot represent a removal any other way. If a delete
 * simply dropped the item, the other phone — still holding it — would push it back and
 * resurrect it. A tombstone is a normal edit that happens to mean "gone", so the same
 * last-write-wins rule settles it. Tombstones are stripped before the UI ever sees them.
 */
sealed interface Logged {
    val id: String
    val updatedAt: Long
    val deleted: Boolean
}

@Serializable
data class Appointment(
    override val id: String = newId(),
    val title: String,
    val atMillis: Long,
    val note: String = "",
    override val updatedAt: Long = System.currentTimeMillis(),
    override val deleted: Boolean = false,
) : Logged

@Serializable
data class WeightEntry(
    override val id: String = newId(),
    val date: Iso8601Date,
    val kg: Double,
    override val updatedAt: Long = System.currentTimeMillis(),
    override val deleted: Boolean = false,
) : Logged

@Serializable
data class Note(
    override val id: String = newId(),
    val createdAtMillis: Long,
    val week: Int,
    val text: String,
    override val updatedAt: Long = System.currentTimeMillis(),
    override val deleted: Boolean = false,
) : Logged

@Serializable
data class KickSession(
    override val id: String = newId(),
    val startedAtMillis: Long,
    val durationMillis: Long,
    val kicks: Int,
    override val updatedAt: Long = System.currentTimeMillis(),
    override val deleted: Boolean = false,
) : Logged

/**
 * A picture — a bump photo, a scan, a keepsake — placed on the pregnancy by its [date].
 *
 * [blob] is the sha256 of the image bytes, which live outside this document as a file
 * (see `PhotoFiles`). Naming the bytes by their hash makes them immutable, so only this
 * metadata ever needs merging; the bytes are just copied to whichever side lacks them.
 */
@Serializable
data class Photo(
    override val id: String = newId(),
    val date: Iso8601Date,
    val caption: String = "",
    val blob: String,
    override val updatedAt: Long = System.currentTimeMillis(),
    override val deleted: Boolean = false,
) : Logged

/**
 * The whole app. One serialisable tree, persisted as a single JSON document.
 *
 * [babyName] is the chosen name and [babyNickname] is what you call the bump before
 * there is one; both are optional and neither replaces the other.
 *
 * [dueDate] is the only derived-from state in the app — everything about the pregnancy
 * itself (week, trimester, countdown, progress) is a pure function of it. A null due
 * date is the app's only state machine: null means show onboarding.
 *
 * [bornOn] and [paused] are the two ways the pregnancy view ends. [bornOn] stops the
 * clock on the day the baby arrived. [paused] is deliberately unexplained: it quietly
 * hides the countdown and weekly size on both phones, for whatever reason that is wanted,
 * and loses nothing.
 *
 * [detailsUpdatedAt] stamps the fields above it as one group. They are not [Logged]
 * items and have no id, so sync cannot merge them field by field; the phone that edited
 * the details most recently wins all four. That is the honest model anyway — they are
 * edited together, on one sheet, as one decision.
 */
@Serializable
data class AppState(
    val dueDate: Iso8601Date? = null,
    val babyName: String = "",
    val babyNickname: String = "",
    val babySex: BabySex = BabySex.UNKNOWN,
    val bornOn: Iso8601Date? = null,
    val paused: Boolean = false,
    val detailsUpdatedAt: Long = 0L,
    val appointments: List<Appointment> = emptyList(),
    val weights: List<WeightEntry> = emptyList(),
    val notes: List<Note> = emptyList(),
    val kickSessions: List<KickSession> = emptyList(),
    val photos: List<Photo> = emptyList(),
)

/**
 * The date the pregnancy is measured at: today, or the birth date once there is one, so
 * week and size freeze where they ended rather than counting on as "overdue".
 */
fun AppState.measuredOn(today: LocalDate): LocalDate = bornOn ?: today

/** The name, the bump name, or nothing yet — in that order. */
val AppState.displayName: String get() = when {
    babyName.isNotBlank() -> babyName
    babyNickname.isNotBlank() -> babyNickname
    else -> "Your baby"
}
