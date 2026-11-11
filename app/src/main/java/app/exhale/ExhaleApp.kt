package app.exhale

import android.app.Application
import app.exhale.data.ExhaleDatabase
import app.exhale.data.ExhaleRepository
import app.exhale.data.ReminderStore
import app.exhale.data.SettingsStore
import app.exhale.notify.ReminderScheduler
import app.exhale.widget.WidgetRefresh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual DI: one repository for the whole process. */
class ExhaleApp : Application() {
    /** Work that outlives a screen or a broadcast, such as redrawing the home-screen widgets. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Which reminders were already posted (or skipped); see [ReminderScheduler]. */
    val reminders: ReminderStore by lazy { ReminderStore(this) }

    val repository: ExhaleRepository by lazy {
        ExhaleRepository(ExhaleDatabase.create(this), SettingsStore(this), onWrite = {
            WidgetRefresh.refreshAllAsync(this)
            ReminderScheduler.runAsync(this)
        })
    }
}
