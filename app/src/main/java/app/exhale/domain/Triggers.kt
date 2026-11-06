package app.exhale.domain

import java.util.UUID

/** One trigger in the user's list, and whether it is hidden from the chips. */
data class TriggerEntry(val trigger: Trigger, val hidden: Boolean = false) {
    val key: String get() = trigger.key
}

/** What a row of trigger chips shows: [visible] in order, and how many more wait behind a "N more" chip. */
data class ChipRow(val visible: List<Trigger>, val more: Int)

/** What [Triggers.add] did: the list to save and the trigger the name now means. */
data class TriggerAdded(val list: List<TriggerEntry>, val trigger: Trigger, val existed: Boolean)

/**
 * The user's own trigger list (Settings → Triggers, and "Your own" after logging). Pure functions on the
 * list; the caller saves what they return.
 *
 * The list is kept as every shown entry in chip order, then the hidden ones. Built-ins can be hidden,
 * moved and deleted, never renamed. Anything that tagged a log or a craving can't be deleted, only
 * hidden, so its history stays.
 */
object Triggers {
    /** Longest name, after tidying ([clean]). */
    const val NAME_MAX = 20

    /** Chips shown before a "N more" chip. A list only one longer shows whole: "1 more" would save nothing. */
    const val CHIPS_COLLAPSED = 9

    /** Keys of the user's own triggers start with this. */
    const val CUSTOM_PREFIX = "custom:"

    /** The list on a fresh install, after Erase and with demo data: the built-ins, all shown. */
    val defaults: List<TriggerEntry> = Trigger.BUILT_IN.map { TriggerEntry(it) }

    fun newKey(): String = CUSTOM_PREFIX + UUID.randomUUID()

    /** The triggers to offer as chips, in order. */
    fun shown(list: List<TriggerEntry>): List<Trigger> = list.filterNot { it.hidden }.map { it.trigger }

    /**
     * Chips for [shown]: the first [CHIPS_COLLAPSED] and a count of the rest, unless [expanded], the list
     * is at most one longer, or [selected] is among the rest (a picked chip is never tucked away).
     */
    fun chipRow(shown: List<Trigger>, selected: Trigger?, expanded: Boolean): ChipRow {
        val selectedLate = selected != null && shown.indexOf(selected) >= CHIPS_COLLAPSED
        return if (expanded || shown.size <= CHIPS_COLLAPSED + 1 || selectedLate) ChipRow(shown, 0)
        else ChipRow(shown.take(CHIPS_COLLAPSED), shown.size - CHIPS_COLLAPSED)
    }

    /** [name] as it would be saved: trimmed, runs of spaces made one, at most [NAME_MAX]. Null when blank. */
    fun clean(name: String): String? =
        name.trim().replace(Regex("\\s+"), " ").take(NAME_MAX).trim().ifEmpty { null }

    /** The entry already called [name], ignoring case and spaces, other than the one with key [except]. */
    fun named(list: List<TriggerEntry>, name: String, except: String? = null): TriggerEntry? {
        val n = norm(name)
        if (n.isEmpty()) return null
        return list.firstOrNull { it.key != except && norm(it.trigger.label) == n }
    }

    /**
     * Adds a trigger called [name]. A name the list already has means that trigger (shown again if it
     * was hidden); the name of a deleted built-in brings the built-in back; anything else becomes a new
     * trigger with [key] at the end of the shown ones. Null when [name] is blank.
     */
    fun add(list: List<TriggerEntry>, name: String, key: String): TriggerAdded? {
        val label = clean(name) ?: return null
        named(list, label)?.let { found ->
            val next = if (found.hidden) setHidden(list, found.key, false) else list
            return TriggerAdded(next, found.trigger, existed = true)
        }
        val trigger = BuiltInTrigger.entries.firstOrNull { norm(it.label) == norm(label) }?.let(Trigger::of)
            ?: Trigger(key, label)
        return TriggerAdded(insertShown(list, TriggerEntry(trigger)), trigger, existed = false)
    }

    /** Renames the user's own trigger [key]. Null when it's a built-in, missing, or [name] is blank or taken. */
    fun rename(list: List<TriggerEntry>, key: String, name: String): List<TriggerEntry>? {
        val label = clean(name) ?: return null
        val entry = list.firstOrNull { it.key == key } ?: return null
        if (entry.trigger.builtIn != null || named(list, label, except = key) != null) return null
        return list.map { if (it.key == key) it.copy(trigger = it.trigger.copy(label = label)) else it }
    }

    /** Hides [key] (it moves after the shown ones) or shows it again (at the end of the shown ones). */
    fun setHidden(list: List<TriggerEntry>, key: String, hidden: Boolean): List<TriggerEntry> {
        val entry = list.firstOrNull { it.key == key } ?: return list
        if (entry.hidden == hidden) return list
        val rest = list.filterNot { it.key == key }
        return if (hidden) rest + entry.copy(hidden = true) else insertShown(rest, entry.copy(hidden = false))
    }

    /** Moves the shown trigger at [from] to [to], both positions among the shown ones. */
    fun move(list: List<TriggerEntry>, from: Int, to: Int): List<TriggerEntry> {
        val shown = list.filterNot { it.hidden }.toMutableList()
        if (from !in shown.indices) return list
        shown.add(to.coerceIn(0, shown.lastIndex), shown.removeAt(from))
        return shown + list.filter { it.hidden }
    }

    /** How many logs and cravings each trigger key tagged. */
    fun uses(logs: List<SmokeLog>, urges: List<Urge>): Map<String, Int> =
        (logs.mapNotNull { it.trigger?.key } + urges.mapNotNull { it.trigger?.key }).groupingBy { it }.eachCount()

    /** Only a trigger that never tagged anything can be deleted. */
    fun canDelete(key: String, uses: Map<String, Int>): Boolean = (uses[key] ?: 0) == 0

    /** [list] without [key], or unchanged when [key] has been used. */
    fun delete(list: List<TriggerEntry>, key: String, uses: Map<String, Int>): List<TriggerEntry> =
        if (canDelete(key, uses)) list.filterNot { it.key == key } else list

    /**
     * The trigger a stored [key] means: from [byKey] (the user's list), else the built-in of that name,
     * so history never loses a built-in tag. Null for no key or an unknown one.
     */
    fun resolve(key: String?, byKey: Map<String, Trigger>): Trigger? {
        if (key == null) return null
        return byKey[key] ?: BuiltInTrigger.entries.firstOrNull { it.name == key }?.let(Trigger::of)
    }

    private fun insertShown(list: List<TriggerEntry>, entry: TriggerEntry): List<TriggerEntry> {
        val at = list.indexOfFirst { it.hidden }.let { if (it < 0) list.size else it }
        return list.take(at) + entry + list.drop(at)
    }

    private fun norm(s: String) = s.lowercase().filterNot { it.isWhitespace() }
}
