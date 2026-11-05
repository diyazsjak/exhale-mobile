package app.exhale.data

import app.exhale.domain.SmokeLog
import app.exhale.domain.Trigger
import app.exhale.domain.Urge
import app.exhale.domain.UrgeOutcome
import java.time.Instant

/** Stored enum names can outlive the enum: an unknown or missing name reads as null. */
internal inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? =
    name?.let { runCatching { enumValueOf<E>(it) }.getOrNull() }

internal fun SmokeLogEntity.toDomain() = SmokeLog(
    id = id,
    at = Instant.ofEpochMilli(atMillis),
    trigger = enumOrNull<Trigger>(trigger),
    fromUrge = fromUrge,
)

internal fun SmokeLog.toEntity() = SmokeLogEntity(
    id = id,
    atMillis = at.toEpochMilli(),
    trigger = trigger?.name,
    fromUrge = fromUrge,
)

internal fun UrgeEntity.toDomain() = Urge(
    id = id,
    startedAt = Instant.ofEpochMilli(startedMillis),
    endedAt = Instant.ofEpochMilli(endedMillis),
    outcome = enumOrNull<UrgeOutcome>(outcome) ?: UrgeOutcome.SURVIVED,
    trigger = enumOrNull<Trigger>(trigger),
)

internal fun Urge.toEntity() = UrgeEntity(
    id = id,
    startedMillis = startedAt.toEpochMilli(),
    endedMillis = endedAt.toEpochMilli(),
    outcome = outcome.name,
    trigger = trigger?.name,
)
