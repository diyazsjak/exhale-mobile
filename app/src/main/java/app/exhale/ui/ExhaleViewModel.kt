package app.exhale.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.exhale.ExhaleApp
import app.exhale.data.ExhaleRepository
import app.exhale.domain.History
import app.exhale.domain.Milestones
import app.exhale.domain.Money
import app.exhale.domain.MoneySummary
import app.exhale.domain.PlanOutlook
import app.exhale.domain.Recovery
import app.exhale.domain.RecoverySummary
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
    /** The user's trigger list: shown ones in chip order, then hidden ones (Settings → Triggers). */
    val triggers: List<TriggerEntry> = Triggers.defaults,
    /** How many logs and cravings each trigger key tagged; only unused ones can be deleted. */
    val triggerUses: Map<String, Int> = emptyMap(),
    /** The triggers offered as chips, in order ([Triggers.shown]). */
    val shownTriggers: List<Trigger> = Triggers.shown(triggers),
) {
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

/** One-shot UI feedback after logging, used for the undo + trigger toast. */
data class LoggedEvent(val logId: Long, val countAfter: Int, val allowance: Int)

class ExhaleViewModel(private val repo: ExhaleRepository) : ViewModel() {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    private val ticker = flow {
        while (true) {
            emit(Instant.now())
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
        val shownTriggers = Triggers.shown(triggers)
    }

    private val stored = combine(repo.settings, repo.logs, repo.urges, repo.triggers, ::Stored)

    val state: StateFlow<ExhaleState> =
        combine(stored, ticker) { d, now -> derive(d, now) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExhaleState())

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
            triggers = d.triggers,
            triggerUses = d.triggerUses,
            shownTriggers = d.shownTriggers,
        )
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
        viewModelScope.launch { repo.tagLog(id, trigger) }
        _lastLogged.value = null
    }

    fun dismissLogged() { _lastLogged.value = null }

    // --- Triggers. Each change is worked out on the saved list under a lock, so quick edits
    // (a drag, say) never build on a list the flow hasn't caught up with yet.
    private val triggerLock = Mutex()

    private fun editTriggers(change: (List<TriggerEntry>) -> List<TriggerEntry>?) {
        viewModelScope.launch {
            triggerLock.withLock {
                val current = repo.triggers.first()
                change(current)?.takeIf { it != current }?.let { repo.saveTriggers(it) }
            }
        }
    }

    /**
     * Adds a trigger called [name], or finds the one already called that ([Triggers.add]), then hands
     * the saved trigger to [then]. Nothing happens for a blank name.
     */
    fun addTrigger(name: String, then: (Trigger) -> Unit = {}) {
        viewModelScope.launch {
            val added = triggerLock.withLock {
                val current = repo.triggers.first()
                Triggers.add(current, name, Triggers.newKey())?.also { if (it.list != current) repo.saveTriggers(it.list) }
            }
            added?.let { then(it.trigger) }
        }
    }

    /** "Your own" on the Logged card: add the trigger and tag the log with it. */
    fun addAndTag(logId: Long, name: String) = addTrigger(name) { tag(logId, it) }

    fun renameTrigger(key: String, name: String) = editTriggers { Triggers.rename(it, key, name) }
    fun setTriggerHidden(key: String, hidden: Boolean) = editTriggers { Triggers.setHidden(it, key, hidden) }
    fun moveTrigger(from: Int, to: Int) = editTriggers { Triggers.move(it, from, to) }
    fun deleteTrigger(key: String) {
        viewModelScope.launch {
            triggerLock.withLock {
                // Counted from what's saved now, so a tag made a moment ago still protects it.
                val uses = Triggers.uses(repo.logs.first(), repo.urges.first())
                val current = repo.triggers.first()
                Triggers.delete(current, key, uses).takeIf { it != current }?.let { repo.saveTriggers(it) }
            }
        }
    }

    fun finishUrge(started: Instant, outcome: UrgeOutcome, trigger: Trigger?) {
        viewModelScope.launch {
            // The urge is recorded either way. A cigarette is only ever logged by holding the ember.
            repo.recordUrge(started, Instant.now(), outcome, trigger)
        }
    }

    fun saveSettings(s: UserSettings) { viewModelScope.launch { repo.saveSettings(s) } }

    fun setTheme(mode: ThemeMode) { saveSettings(state.value.settings.copy(theme = mode)) }
    fun setHome(style: HomeStyle) { saveSettings(state.value.settings.copy(home = style)) }

    /** The user opened today's "borrowed from yesterday" note: hide the badge dot until next time. */
    fun markBorrowNoticeSeen() {
        val st = state.value
        if (!st.settings.borrowNoticeSeenOn(st.today)) saveSettings(st.settings.withBorrowNoticeSeen(st.today))
    }

    fun loadDemo() { viewModelScope.launch { repo.seedDemo() } }

    fun eraseAll() { viewModelScope.launch { repo.eraseAll() } }

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
