package app.exhale.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.exhale.ExhaleApp
import app.exhale.data.ExhaleRepository
import app.exhale.domain.DayTimes
import app.exhale.domain.Export
import app.exhale.domain.Import
import app.exhale.domain.History
import app.exhale.domain.Milestones
import app.exhale.domain.Money
import app.exhale.domain.MoneySummary
import app.exhale.domain.PlanOutlook
import app.exhale.domain.Recovery
import app.exhale.domain.RecoverySummary
import app.exhale.domain.ReminderKind
import app.exhale.domain.ReminderOutlook
import app.exhale.domain.Reminders
import app.exhale.domain.SmokeLog
import app.exhale.domain.Stats
import app.exhale.domain.HomeStyle
import app.exhale.domain.ThemeMode
import app.exhale.domain.TodayPlan
import app.exhale.domain.Trigger
import app.exhale.domain.TriggerEntry
import app.exhale.domain.Triggers
import app.exhale.domain.Urge
import app.exhale.domain.UrgeOutcome
import app.exhale.domain.UserSettings
import app.exhale.ui.components.DayDot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Everything the screens render, derived once per tick. */
data class ExhaleState(
    val loaded: Boolean = false,
    val now: Instant = Instant.now(),
    val settings: UserSettings = UserSettings(),
    val logs: List<SmokeLog> = emptyList(),
    val urges: List<Urge> = emptyList(),
    /** Today against the plan: count, allowance, interval, last log, heat and what goes over. */
    val plan: TodayPlan = TodayPlan.of(settings, emptyList(), now, ZoneId.systemDefault()),
    /** Everything the Money screen shows. */
    val money: MoneySummary? = null,
    val clearFraction: Float = 1f,
    /** Everything the Air screen shows besides [sinceLast]. */
    val recovery: RecoverySummary? = null,
    /** When tracking began: the plan start, or the first log if earlier. Null with neither. */
    val dataStart: LocalDate? = null,
    /** The last 7 days, today last, for the week strip on Rhythm. */
    val week: List<DayDot> = emptyList(),
    /** Today cigarette by cigarette, for the row under the app bar on Ember. */
    val todayTimes: DayTimes? = null,
    /** The user's trigger list: shown ones in chip order, then hidden ones (Settings → Triggers). */
    val triggers: List<TriggerEntry> = Triggers.defaults,
    /** How many logs and cravings each trigger key tagged; only unused ones can be deleted. */
    val triggerUses: Map<String, Int> = emptyMap(),
    /** What Settings → Reminders says: the busy hours, and why a kind won't come for now ([Reminders.outlook]). */
    val reminderOutlook: ReminderOutlook = ReminderOutlook(),
) {
    /** The triggers offered as chips, in order ([Triggers.shown]). */
    val shownTriggers: List<Trigger> get() = Triggers.shown(triggers)

    // Today's numbers live in [plan] (domain/TodayPlan, unit-tested); these read through to it.
    val today: LocalDate get() = plan.date
    val todayCount: Int get() = plan.count
    val allowance: Int get() = plan.allowance
    val intervalMinutes: Int get() = plan.intervalMinutes
    val lastLog: SmokeLog? get() = plan.last
    val sinceLast: Duration? get() = plan.sinceLast
    val nextAllowedAt: Instant? get() = plan.nextAllowedAt
    val heat: Float get() = plan.heat
    val borrowedToday: Int get() = plan.borrowed
    val remainingToNext: Duration? get() = plan.remainingToNext
    val earned: Boolean get() = plan.earned
    val earnedFraction: Float get() = plan.earnedFraction
    val overAllowance: Boolean get() = plan.overAllowance
    val overToday: Int get() = plan.overToday
    val leftToday: Int get() = plan.leftToday
    val borrowingFromTomorrow: Int get() = plan.borrowingFromTomorrow
    val tomorrowAllowance: Int get() = plan.tomorrowAllowance
    val borrowNoticeDue: Boolean get() = plan.borrowNoticeDue
    val outlook: PlanOutlook get() = plan.outlook
}

/** What Settings asks after a file was picked to import. */
sealed interface ImportAsk {
    /** The file was read: what importing it would add and leave out. */
    data class Ready(val plan: Import.Plan) : ImportAsk
    /** It couldn't be opened, or isn't a file Exhale can read. */
    data object Unreadable : ImportAsk
}

/** One-shot UI feedback after logging, used for the undo + trigger toast. */
data class LoggedEvent(val logId: Long, val countAfter: Int, val allowance: Int)

class ExhaleViewModel(private val repo: ExhaleRepository) : ViewModel() {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    /** Once a second, so what depends on the time (countdowns, money, recovery) moves on. */
    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(1000)
        }
    }

    /** What's stored, with what follows from it alone; only changes when the data does, not every tick. */
    private class Stored(
        val settings: UserSettings,
        val logs: List<SmokeLog>,
        val urges: List<Urge>,
        val triggers: List<TriggerEntry>,
    ) {
        val triggerUses = Triggers.uses(logs, urges)
    }

    private val stored = combine(repo.settings, repo.logs, repo.urges, repo.triggers, ::Stored)

    /**
     * Worked out again on every tick and whenever the data changes, always with the time it is
     * worked out at. Not the last tick's time: right after a log that is up to a second older than
     * the log, so the countdown showed the whole gap and a second more ("1h 0m" of 60) until the next
     * tick took it under ("59m"), and the hero changed twice in a row.
     */
    val state: StateFlow<ExhaleState> =
        combine(stored, ticker) { d, _ -> derive(d, Instant.now()) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExhaleState())

    private val _lastLogged = MutableStateFlow<LoggedEvent?>(null)
    val lastLogged: StateFlow<LoggedEvent?> = _lastLogged.asStateFlow()

    private fun derive(d: Stored, now: Instant): ExhaleState {
        val s = d.settings
        val logs = d.logs
        val plan = TodayPlan.of(s, logs, now, zone)
        val dataStart = Stats.dataStart(s.planStart, logs, zone)
        return ExhaleState(
            loaded = true,
            now = now,
            settings = s,
            logs = logs,
            urges = d.urges,
            plan = plan,
            money = Money.summary(s, logs, now, zone),
            clearFraction = Milestones.clearFraction(plan.sinceLast),
            recovery = Recovery.summary(s, logs, now, zone),
            dataStart = dataStart,
            week = weekDays(s, logs, plan.date, zone, dataStart),
            todayTimes = DayTimes.of(logs, d.urges, plan.date, zone, plan.allowance, now),
            triggers = d.triggers,
            triggerUses = d.triggerUses,
            reminderOutlook = outlookFor(d, now),
        )
    }

    /** The last [Reminders.outlook]: for this data and this minute. It moves with the clock, but not every second. */
    private var outlook: Triple<Stored, Long, ReminderOutlook>? = null

    private fun outlookFor(d: Stored, now: Instant): ReminderOutlook {
        val minute = now.epochSecond / 60
        outlook?.let { (data, m, o) -> if (data === d && m == minute) return o }
        return Reminders.outlook(d.settings, d.logs, now, zone).also { outlook = Triple(d, minute, it) }
    }

    fun log(trigger: Trigger? = null, fromUrge: Boolean = false) {
        viewModelScope.launch {
            val id = repo.logCigarette(trigger = trigger, fromUrge = fromUrge)
            val st = state.value
            _lastLogged.value = LoggedEvent(id, st.todayCount + 1, st.allowance)
        }
    }

    fun undo(id: Long) {
        viewModelScope.launch { repo.undoLog(id) }
        _lastLogged.value = null
    }

    fun tag(id: Long, trigger: Trigger) {
        viewModelScope.launch { triggerLock.withLock { repo.tagLog(id, trigger) } }
        _lastLogged.value = null
    }

    fun dismissLogged() { _lastLogged.value = null }

    /**
     * The day list's edit: [id] moves to [at] with [trigger] (null clears it). A save that changes
     * nothing writes nothing. Takes the tag lock, like [tag].
     */
    fun editLog(id: Long, at: Instant, trigger: Trigger?) {
        val log = state.value.logs.firstOrNull { it.id == id }
        if (log != null && log.at == at && log.trigger == trigger) return
        viewModelScope.launch { triggerLock.withLock { repo.editLog(id, at, trigger) } }
    }

    /**
     * The day list's "Add one you forgot": a cigarette at [at] with [trigger] (or none). No Logged
     * card and no haptic: it's a correction, not one smoked now. Takes the tag lock, like [editLog].
     */
    fun addLog(at: Instant, trigger: Trigger?) {
        viewModelScope.launch { triggerLock.withLock { repo.logCigarette(trigger, at = at) } }
    }

    /**
     * The day list's edit of a craving wave: only its trigger (null clears it); when and how long
     * it was stay. A save that changes nothing writes nothing. Takes the tag lock, like [tag].
     */
    fun tagUrge(id: Long, trigger: Trigger?) {
        if (state.value.urges.firstOrNull { it.id == id }?.trigger == trigger) return
        viewModelScope.launch { triggerLock.withLock { repo.tagUrge(id, trigger) } }
    }

    /** The day list's delete of a craving wave. Takes the tag lock, so it can't slip between a tag's read and save. */
    fun deleteUrge(id: Long) {
        viewModelScope.launch { triggerLock.withLock { repo.deleteUrge(id) } }
    }

    /** The day list's delete. A Logged card still showing this log closes. */
    fun deleteLog(id: Long) {
        viewModelScope.launch { repo.undoLog(id) }
        if (_lastLogged.value?.logId == id) _lastLogged.value = null
    }

    // --- Triggers. Each change is worked out on the saved list under a lock, so quick edits
    // (a drag, say) never build on a list the flow hasn't caught up with yet.
    private val triggerLock = Mutex()

    // Tags, cravings, erasing and demo data take the same lock, so none of them can slip between an
    // edit's read and its save (a tag landing on a trigger being deleted, say).
    // [alsoLocked] runs after the save, still under the lock; [after] runs once it's released.
    private fun editTriggers(
        after: () -> Unit = {},
        alsoLocked: suspend () -> Unit = {},
        change: suspend (List<TriggerEntry>) -> List<TriggerEntry>?,
    ) {
        viewModelScope.launch {
            triggerLock.withLock {
                val current = repo.triggers.first()
                change(current)?.takeIf { it != current }?.let { repo.saveTriggers(it) }
                alsoLocked()
            }
            after()
        }
    }

    /**
     * Adds a trigger called [name], or finds the one already called that ([Triggers.add]), then hands
     * the saved trigger to [then]. Nothing happens for a blank name.
     */
    fun addTrigger(name: String, then: (Trigger) -> Unit = {}) {
        var added: Trigger? = null
        editTriggers(after = { added?.let(then) }) { list ->
            Triggers.add(list, name, Triggers.newKey())?.also { added = it.trigger }?.list
        }
    }

    /**
     * "Your own" on the Logged card: adds the trigger and tags log [logId] with it, whatever happens to
     * the card meanwhile; [then] gets the trigger so the card can show it picked before it closes.
     */
    fun addAndTag(logId: Long, name: String, then: (Trigger) -> Unit = {}) {
        var added: Trigger? = null
        // Saved and tagged under one lock, so no delete can slip in between and leave the tag pointing nowhere.
        editTriggers(after = { added?.let(then) }, alsoLocked = { added?.let { repo.tagLog(logId, it) } }) { list ->
            Triggers.add(list, name, Triggers.newKey())?.also { added = it.trigger }?.list
        }
    }

    fun renameTrigger(key: String, name: String) = editTriggers { Triggers.rename(it, key, name) }
    fun setTriggerHidden(key: String, hidden: Boolean) = editTriggers { Triggers.setHidden(it, key, hidden) }
    fun moveTrigger(from: Int, to: Int) = editTriggers { Triggers.move(it, from, to) }
    /** Counted from the stored tags, so even a tag made a moment ago keeps it. */
    fun deleteTrigger(key: String) = editTriggers { Triggers.delete(it, key, mapOf(key to repo.uses(key))) }

    fun finishUrge(started: Instant, outcome: UrgeOutcome, trigger: Trigger?) {
        viewModelScope.launch {
            // The urge is recorded either way. A cigarette is only ever logged by holding the ember.
            triggerLock.withLock { repo.recordUrge(started, Instant.now(), outcome, trigger) }
        }
    }

    fun saveSettings(s: UserSettings) { viewModelScope.launch { repo.saveSettings(s) } }

    fun setTheme(mode: ThemeMode) { saveSettings(state.value.settings.copy(theme = mode)) }
    fun setHome(style: HomeStyle) { saveSettings(state.value.settings.copy(home = style)) }
    fun setReminder(kind: ReminderKind, on: Boolean) { viewModelScope.launch { repo.setReminder(kind, on) } }

    /** The user opened today's "borrowed from yesterday" note: hide the badge dot until next time. */
    fun markBorrowNoticeSeen() {
        val st = state.value
        if (!st.settings.borrowNoticeSeenOn(st.today)) saveSettings(st.settings.withBorrowNoticeSeen(st.today))
    }

    /**
     * Settings → Export as CSV: writes everything to the stream [open] gives (the file the user picked),
     * then hands [done] what it holds, or null when the file couldn't be opened or written.
     */
    fun exportCsv(open: () -> OutputStream?, done: (Export.Counts?) -> Unit) {
        viewModelScope.launch {
            val counts = withContext(Dispatchers.IO) {
                try {
                    open()?.use { repo.exportCsv(it) }
                } catch (e: IOException) {
                    null
                } catch (e: SecurityException) {
                    null
                }
            }
            done(counts)
        }
    }

    private val _importAsk = MutableStateFlow<ImportAsk?>(null)
    /** Settings → Import from CSV: what to ask about the file picked; null while nothing is asked. */
    val importAsk: StateFlow<ImportAsk?> = _importAsk.asStateFlow()
    private var importRows: Import.Rows? = null

    /** Reads the file [open] gives (the one the user picked) and works out what importing it would do. */
    fun readImport(open: () -> InputStream?) {
        viewModelScope.launch {
            val rows = withContext(Dispatchers.IO) {
                try {
                    open()?.use(::readCapped)?.let { Import.parse(it, zone) }
                } catch (e: IOException) {
                    null
                } catch (e: SecurityException) {
                    null
                }
            }
            importRows = rows
            _importAsk.value = if (rows == null) ImportAsk.Unreadable
            else ImportAsk.Ready(triggerLock.withLock { repo.importPlan(rows, Instant.now()) })
        }
    }

    /**
     * Adds what the file read last has that isn't here, worked out again against the data as it is now
     * (under the trigger lock, so a tag or edit can't slip in between), then hands [done] what was added.
     */
    fun confirmImport(done: (Export.Counts) -> Unit) {
        val rows = importRows ?: return
        dismissImport()
        viewModelScope.launch {
            val plan = triggerLock.withLock { repo.importPlan(rows, Instant.now()).also { repo.applyImport(it) } }
            done(plan.counts)
        }
    }

    fun dismissImport() {
        _importAsk.value = null
        importRows = null
    }

    fun loadDemo() { viewModelScope.launch { triggerLock.withLock { repo.seedDemo() } } }

    fun eraseAll() { viewModelScope.launch { triggerLock.withLock { repo.eraseAll() } } }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ExhaleApp
                ExhaleViewModel(app.repository)
            }
        }
    }
}

/** The last 7 days, today last, for the week strip. Days before tracking began have no count; days before the plan start have no allowance. */
fun weekDays(s: UserSettings, logs: List<SmokeLog>, today: LocalDate, zone: ZoneId, dataStart: LocalDate?): List<DayDot> =
    History.week(s, logs, today, zone, dataStart).map { (date, t) -> DayDot(date, t?.count, t?.allowance) }

/** Largest file Settings imports: far more than years of logs, small enough to read at once. */
private const val IMPORT_MAX_BYTES = 16 * 1024 * 1024

/** [input] as UTF-8 text; a file over [IMPORT_MAX_BYTES] reads as null. */
private fun readCapped(input: InputStream): String? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    while (true) {
        val n = input.read(buffer)
        if (n < 0) break
        out.write(buffer, 0, n)
        if (out.size() > IMPORT_MAX_BYTES) return null
    }
    return out.toString(Charsets.UTF_8.name())
}
