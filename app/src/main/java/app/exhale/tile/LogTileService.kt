package app.exhale.tile

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.exhale.ExhaleApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Quick-settings tile: one tap logs a cigarette without opening the app; the subtitle shows today's
 * count. Room's suspend DAOs move off the main thread themselves, so the tile's scope stays on Main.
 */
class LogTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val repo get() = (application as ExhaleApp).repository

    override fun onStartListening() {
        super.onStartListening()
        scope.launch { refresh() }
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            repo.logCigarette()
            refresh()
        }
    }

    private suspend fun refresh() {
        val count = repo.countToday()
        val tile = qsTile ?: return
        tile.state = Tile.STATE_INACTIVE
        tile.label = "Log cigarette"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = "$count today"
        tile.updateTile()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
