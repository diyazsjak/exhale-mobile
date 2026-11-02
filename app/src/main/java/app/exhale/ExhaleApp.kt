package app.exhale

import android.app.Application
import app.exhale.data.ExhaleDatabase
import app.exhale.data.ExhaleRepository
import app.exhale.data.SettingsStore

/** Manual DI: one repository for the whole process. */
class ExhaleApp : Application() {
    val repository: ExhaleRepository by lazy {
        ExhaleRepository(ExhaleDatabase.create(this), SettingsStore(this))
    }
}
