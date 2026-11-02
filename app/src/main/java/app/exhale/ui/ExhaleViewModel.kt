package app.exhale.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.exhale.ExhaleApp
import app.exhale.data.ExhaleRepository
import app.exhale.domain.Milestones
import app.exhale.domain.PlanMath
import app.exhale.domain.SmokeLog
import app.exhale.domain.Stats
import app.exhale.domain.Trigger
import app.exhale.domain.Urge
import app.exhale.domain.UrgeOutcome
import app.exhale.domain.UserSettings
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
    val notSmoked: Int = 0,
    val savedMinor: Long = 0,
    val urgesSurvived: Int = 0,
    val longestGapWeek: Duration = Duration.ZERO,
    val clearFraction: Float = 1f,
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
        val today = now.atZone(zone).toLocalDate()
        val todayCount = Stats.countOn(logs, today, zone)
        val yesterdayCount = Stats.countOn(logs, today.minusDays(1), zone)
        val allowance = PlanMath.allowance(s, today, yesterdayCount)
        val interval = PlanMath.intervalMinutes(allowance, s.wakingMinutes)
        val last = logs.lastOrNull()
        val sinceLast = last?.let { Duration.between(it.at, now) }
        val planStartInstant = s.planStart.atStartOfDay(zone).toInstant()
        val smokedSince = logs.count { it.at >= planStartInstant }
        val notSmoked = Stats.notSmoked(s.baselinePerDay, planStartInstant, now, smokedSince)
        return ExhaleState(
            loaded = true,
            now = now,
            settings = s,
            logs = logs,
            urges = urges,
            today = today,
            todayCount = todayCount,
            allowance = allowance,
            intervalMinutes = interval,
            lastLog = last,
            sinceLast = sinceLast,
            nextAllowedAt = last?.at?.plus(Duration.ofMinutes(interval.toLong())),
            heat = Stats.heat(now, last?.at, interval),
            borrowedToday = PlanMath.debt(s, today.minusDays(1), yesterdayCount),
            notSmoked = notSmoked,
            savedMinor = Stats.moneySavedMinor(notSmoked, s.pricePerCigMinor),
            urgesSurvived = urges.count { it.outcome == UrgeOutcome.SURVIVED },
            longestGapWeek = Stats.longestGap(logs, now, Duration.ofDays(7)),
            clearFraction = Milestones.clearFraction(sinceLast),
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
            repo.recordUrge(started, Instant.now(), outcome, trigger)
            if (outcome == UrgeOutcome.SMOKED) repo.logCigarette(trigger = trigger, fromUrge = true)
        }
    }

    fun saveSettings(s: UserSettings) { viewModelScope.launch { repo.saveSettings(s) } }

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
