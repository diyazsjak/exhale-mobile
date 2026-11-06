package app.exhale.data

import app.exhale.domain.SmokeLog
import app.exhale.domain.Trigger
import app.exhale.domain.TriggerEntry
import app.exhale.domain.Triggers
import app.exhale.domain.Urge
import app.exhale.domain.UrgeOutcome
import java.time.Instant

/** Stored enum names can outlive the enum: an unknown or missing name reads as null. */
internal inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? =
    name?.let { runCatching { enumValueOf<E>(it) }.getOrNull() }

internal fun SmokeLogEntity.toDomain(triggers: Map<String, Trigger>) = SmokeLog(
    id = id,
    at = Instant.ofEpochMilli(atMillis),
    trigger = Triggers.resolve(trigger, triggers),
    fromUrge = fromUrge,
)

internal fun SmokeLog.toEntity() = SmokeLogEntity(
    id = id,
    atMillis = at.toEpochMilli(),
    trigger = trigger?.key,
    fromUrge = fromUrge,
)

internal fun UrgeEntity.toDomain(triggers: Map<String, Trigger>) = Urge(
    id = id,
    startedAt = Instant.ofEpochMilli(startedMillis),
    endedAt = Instant.ofEpochMilli(endedMillis),
    outcome = enumOrNull<UrgeOutcome>(outcome) ?: UrgeOutcome.SURVIVED,
    trigger = Triggers.resolve(trigger, triggers),
)

internal fun Urge.toEntity() = UrgeEntity(
    id = id,
    startedMillis = startedAt.toEpochMilli(),
    endedMillis = endedAt.toEpochMilli(),
    outcome = outcome.name,
    trigger = trigger?.key,
)

/** Built-ins keep their own label (they can't be renamed); the stored one is for the user's own. */
internal fun TriggerEntity.toDomain() = TriggerEntry(
    Trigger.BUILT_IN.firstOrNull { it.key == id } ?: Trigger(id, label),
    hidden,
)

internal fun TriggerEntry.toEntity(position: Int) = TriggerEntity(key, trigger.label, position, hidden)
