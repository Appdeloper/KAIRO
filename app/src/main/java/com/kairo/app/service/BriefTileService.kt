package com.kairo.app.service

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.kairo.app.R
import com.kairo.app.ui.briefing.BriefingActivity

/**
 * Quick Settings tile "KAIRO": one tap opens the briefing. This is the fallback entry point for
 * phones where widgets or shake detection are restricted by the OEM (CLAUDE.md rule 7).
 */
class BriefTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        tile.label = getString(R.string.tile_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = getString(R.string.tile_subtitle)
        tile.state = Tile.STATE_INACTIVE
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        // The briefing shows the user's day, so on a locked phone ask for unlock first.
        if (isLocked) unlockAndRun { openBriefing() } else openBriefing()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openBriefing() {
        val intent = BriefingActivity.intent(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // API 34+: only the PendingIntent overload is allowed.
            startActivityAndCollapse(PendingIntent.getActivity(this, REQUEST_CODE, intent, FLAGS, creatorOptions()))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    /**
     * Apps targeting API 35+ must opt in, as the PendingIntent's creator, to letting it start an
     * activity. SystemUI sends it in response to the user's tap.
     */
    private fun creatorOptions() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        ActivityOptions.makeBasic().apply {
            @Suppress("DEPRECATION")
            pendingIntentCreatorBackgroundActivityStartMode = ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
        }.toBundle()
    } else {
        null
    }

    private companion object {
        const val REQUEST_CODE = 41
        const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    }
}
