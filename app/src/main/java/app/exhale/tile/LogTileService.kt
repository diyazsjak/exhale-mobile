package app.exhale.tile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.exhale.ExhaleApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Room suspend DAOs run off the main thread themselves, so the tile scope can stay on Main. */
/** Quick-settings tile: one tap logs a cigarette without opening the app. */
class LogTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val repo get() = (application as ExhaleApp).repository

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            repo.logCigarette()
            refresh()
        }
    }

    private fun refresh() {
        scope.launch {
            val count = repo.countToday()
            val tile = qsTile ?: return@launch
            tile.state = Tile.STATE_INACTIVE
            tile.label = "Log cigarette"
            if (android.os.Build.VERSION.SDK_INT >= 29) tile.subtitle = "$count today"
            tile.updateTile()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
