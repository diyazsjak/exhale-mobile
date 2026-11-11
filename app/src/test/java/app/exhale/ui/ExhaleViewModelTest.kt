package app.exhale.ui

import android.content.Context
import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import app.exhale.data.ExhaleDatabase
import app.exhale.data.ExhaleRepository
import app.exhale.data.SettingsStore
import app.exhale.domain.Trigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * The ViewModel over a real database. Here the main thread's clock stands still unless a test moves
 * it, so the once-a-second tick doesn't come between steps; wall-clock time (a log's) runs as usual.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExhaleViewModelTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    // The state is worked out with the time it is worked out at, never the last tick's: right after a
    // log the countdown is already under the whole gap ("59m" of 60). With the last tick's time, from
    // before the log, it showed "1h 0m" until the next tick, so the hero changed twice in a row.
    @Test fun rightAfterALog_theCountdownIsAlreadyUnderTheWholeGap() = withVm { vm, states ->
        vm.log()
        awaitOnMain { states.any { it.logs.isNotEmpty() } }

        val first = states.first { it.logs.isNotEmpty() }
        val logged = first.logs.last().at
        assertFalse("worked out at ${first.now}, before the log at $logged", first.now.isBefore(logged))
        val gap = Duration.ofMinutes(first.plan.intervalMinutes.toLong())
        assertEquals(Fmt.gap(gap.minusSeconds(1)), Fmt.gap(first.remainingToNext))
    }

    // "Add one you forgot" on the day list: the log lands at the chosen moment with its trigger, in
    // time order among the others, and no Logged card opens for it.
    @Test fun addLog_landsAtItsTime_inOrder_withoutALoggedCard() = withVm { vm, states ->
        vm.log()
        awaitOnMain { states.any { it.logs.size == 1 } }
        vm.dismissLogged()
        val forgotten = Instant.now().minus(Duration.ofDays(1)).truncatedTo(ChronoUnit.MINUTES)
        vm.addLog(forgotten, Trigger.COFFEE)
        awaitOnMain { states.any { it.logs.size == 2 } }

        val logs = states.last().logs
        assertEquals(forgotten, logs.first().at)
        assertEquals(Trigger.COFFEE, logs.first().trigger)
        assertFalse(logs.first().fromUrge)
        assertNull(vm.lastLogged.value)
    }

    /**
     * A ViewModel over a fresh database, its states collected, loaded before [test] runs. Held in a
     * store, so clearing it at the end stops its tick and flows before the database closes.
     */
    private fun withVm(test: (ExhaleViewModel, List<ExhaleState>) -> Unit) {
        val db = ExhaleDatabase.create(context)
        val repo = ExhaleRepository(db, SettingsStore(context))
        val store = ViewModelStore()
        val vm = ViewModelProvider.create(store, viewModelFactory { initializer { ExhaleViewModel(repo) } })[ExhaleViewModel::class]
        val states = mutableListOf<ExhaleState>()
        val scope = CoroutineScope(Dispatchers.Main)
        scope.launch { vm.state.collect { states += it } }
        try {
            awaitOnMain { states.any { it.loaded } }
            test(vm, states)
        } finally {
            scope.cancel()
            store.clear()
            db.close()
        }
    }

    /** Runs the main thread's queue until [done], while Room and DataStore work on their own threads. */
    private fun awaitOnMain(done: () -> Boolean) {
        repeat(1_000) {
            if (done()) return
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        throw AssertionError("Timed out waiting on the ViewModel")
    }
}
