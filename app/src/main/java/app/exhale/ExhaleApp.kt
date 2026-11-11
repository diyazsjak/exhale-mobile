package app.exhale

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import app.exhale.data.ExhaleDatabase
import app.exhale.data.ExhaleRepository
import app.exhale.data.ReminderStore
import app.exhale.data.SettingsStore
import app.exhale.notify.ReminderScheduler
import app.exhale.widget.WidgetRefresh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Manual DI: one repository for the whole process. */
class ExhaleApp : Application() {
    /** Work that outlives a screen or a broadcast, such as redrawing the home-screen widgets. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val repository: ExhaleRepository by lazy {
        ExhaleRepository(ExhaleDatabase.create(this), SettingsStore(this), ReminderStore(this), onWrite = {
            WidgetRefresh.refreshAllAsync(this)
            ReminderScheduler.runAsync(this)
        })
    }
}

/**
 * Runs [work] on the app's scope after `onReceive` returns, keeping the broadcast alive until it's
 * done. Failures are dropped: a receiver has no one to tell.
 */
fun BroadcastReceiver.launchAsync(context: Context, work: suspend (ExhaleApp) -> Unit) {
    val app = context.applicationContext as ExhaleApp
    val done = goAsync()
    app.scope.launch {
        runCatching { work(app) }
        done.finish()
    }
}
