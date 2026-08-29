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
 * Every logged item carries a stable [id] and an [updatedAt] stamp.
 *
 * They earn their place locally — [id] gives stable list keys and makes edit/delete/undo
 * unambiguous, [updatedAt] orders edits. They are also exactly what a future two-device
 * sync needs, so adding them now costs nothing and avoids a migration later.
 */
sealed interface Logged {
    val id: String
    val updatedAt: Long
}

@Serializable
data class Appointment(
    override val id: String = newId(),
    val title: String,
    val atMillis: Long,
    val note: String = "",
    override val updatedAt: Long = System.currentTimeMillis(),
) : Logged

@Serializable
data class WeightEntry(
    override val id: String = newId(),
    val date: Iso8601Date,
    val kg: Double,
    override val updatedAt: Long = System.currentTimeMillis(),
) : Logged

@Serializable
data class Note(
    override val id: String = newId(),
    val createdAtMillis: Long,
    val week: Int,
    val text: String,
    override val updatedAt: Long = System.currentTimeMillis(),
) : Logged

@Serializable
data class KickSession(
    override val id: String = newId(),
    val startedAtMillis: Long,
    val durationMillis: Long,
    val kicks: Int,
    override val updatedAt: Long = System.currentTimeMillis(),
) : Logged

/**
 * The whole app. One serialisable tree, persisted as a single JSON document.
 *
 * [dueDate] is the only derived-from state in the app — everything about the pregnancy
 * itself (week, trimester, countdown, progress) is a pure function of it. A null due
 * date is the app's only state machine: null means show onboarding.
 */
@Serializable
data class AppState(
    val dueDate: Iso8601Date? = null,
    val babyName: String = "",
    val appointments: List<Appointment> = emptyList(),
    val weights: List<WeightEntry> = emptyList(),
    val notes: List<Note> = emptyList(),
    val kickSessions: List<KickSession> = emptyList(),
)
