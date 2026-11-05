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
import app.exhale.domain.Recovery
import app.exhale.domain.RecoverySummary
import app.exhale.domain.SmokeLog
import app.exhale.domain.Stats
import app.exhale.domain.ThemeMode
import app.exhale.domain.TodayPlan
import app.exhale.domain.Trigger
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    val today: LocalDate = LocalDate.now(),
    val todayCount: Int = 0,
    val allowance: Int = 0,
    val intervalMinutes: Int = 60,
    val lastLog: SmokeLog? = null,
    val sinceLast: Duration? = null,
    val nextAllowedAt: Instant? = null,
    val heat: Float = 0f,
    val borrowedToday: Int = 0,
    /** Everything the Money screen shows. */
    val money: MoneySummary? = null,
    val clearFraction: Float = 1f,
    /** Everything the Air screen shows besides [sinceLast]. */
    val recovery: RecoverySummary? = null,
    /** When tracking began: the plan start, or the first log if earlier. Null with neither. */
    val dataStart: LocalDate? = null,
    /** The last 7 days, today last, for the week strip on Rhythm. */
    val week: List<DayDot> = emptyList(),
) {
    val remainingToNext: Duration?
        get() = nextAllowedAt?.let { Duration.between(now, it) }?.takeIf { !it.isNegative && !it.isZero }
    val overAllowance: Boolean get() = todayCount >= allowance
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

    val state: StateFlow<ExhaleState> =
        combine(repo.settings, repo.logs, repo.urges, ticker) { s, logs, urges, now ->
            derive(s, logs, urges, now)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExhaleState())

    private val _lastLogged = MutableStateFlow<LoggedEvent?>(null)
    val lastLogged: StateFlow<LoggedEvent?> = _lastLogged.asStateFlow()

    private fun derive(s: UserSettings, logs: List<SmokeLog>, urges: List<Urge>, now: Instant): ExhaleState {
        val plan = TodayPlan.of(s, logs, now, zone)
        val dataStart = Stats.dataStart(s.planStart, logs, zone)
        return ExhaleState(
            loaded = true,
            now = now,
            settings = s,
            logs = logs,
            urges = urges,
            today = plan.date,
            todayCount = plan.count,
            allowance = plan.allowance,
            intervalMinutes = plan.intervalMinutes,
            lastLog = plan.last,
            sinceLast = plan.sinceLast,
            nextAllowedAt = plan.nextAllowedAt,
            heat = plan.heat,
            borrowedToday = plan.borrowed,
            money = Money.summary(s, logs, now, zone),
            clearFraction = Milestones.clearFraction(plan.sinceLast),
            recovery = Recovery.summary(s, logs, now, zone),
            dataStart = dataStart,
            week = weekDays(s, logs, plan.date, zone, dataStart),
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

    fun finishUrge(started: Instant, outcome: UrgeOutcome, trigger: Trigger?) {
        viewModelScope.launch {
            // The urge is recorded either way. A cigarette is only ever logged by holding the ember.
            repo.recordUrge(started, Instant.now(), outcome, trigger)
        }
    }

    fun saveSettings(s: UserSettings) { viewModelScope.launch { repo.saveSettings(s) } }

    fun setTheme(mode: ThemeMode) { saveSettings(state.value.settings.copy(theme = mode)) }

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
