package com.kairo.app.ui.navigation

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.service.quicksettings.TileService
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairo.app.MainActivity
import com.kairo.app.R
import com.kairo.app.service.BriefTileService
import com.kairo.app.ui.alarms.AlarmRingActivity
import com.kairo.app.ui.briefing.BriefingActivity
import com.kairo.app.ui.focus.FocusActivity
import com.kairo.app.ui.plan.PlanSegment
import com.kairo.app.ui.widget.KairoWidgetReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

/**
 * Every way into the app must still land on a real, declared component after the navigation
 * restructure: launcher, shortcut, tile, widget, the orb button and every notification tap.
 */
@RunWith(AndroidJUnit4::class)
class EntryPointsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val pm = context.packageManager

    private fun assertResolves(intent: Intent) {
        assertNotNull("No activity for $intent", pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY or PackageManager.MATCH_ALL))
    }

    @Test fun launcherOpensMainActivity() {
        val launch = pm.getLaunchIntentForPackage(context.packageName)
        assertEquals(MainActivity::class.java.name, launch?.component?.className)
    }

    @Test fun shortcutTargetsTheBriefing() {
        val parser = context.resources.getXml(R.xml.shortcuts)
        var target: String? = null
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "intent") {
                target = parser.getAttributeValue("http://schemas.android.com/apk/res/android", "targetClass")
            }
        }
        assertEquals(BriefingActivity::class.java.name, target)
        assertResolves(Intent().setComponent(ComponentName(context, target!!)))
    }

    @Test fun tileServiceIsDeclaredAndOpensTheBriefing() {
        val services = pm.queryIntentServices(Intent(TileService.ACTION_QS_TILE).setPackage(context.packageName), 0)
        assertTrue(services.any { it.serviceInfo.name == BriefTileService::class.java.name })
        assertResolves(BriefingActivity.intent(context))
    }

    @Test fun widgetReceiverIsDeclared() {
        val receivers = pm.queryBroadcastReceivers(Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).setPackage(context.packageName), 0)
        assertTrue(receivers.any { it.activityInfo.name == KairoWidgetReceiver::class.java.name })
    }

    @Test fun orbButtonOpensTheBriefingReadyToListen() {
        val intent = BriefingActivity.voiceIntent(context)
        assertTrue(intent.getBooleanExtra(BriefingActivity.EXTRA_LISTEN, false))
        assertResolves(intent)
    }

    @Test fun notificationTargetsResolve() {
        assertResolves(FocusActivity.sessionIntent(context, 1))
        assertResolves(FocusActivity.nextStepIntent(context, 1))
        assertResolves(MainActivity.rearmShakeIntent(context))
        assertResolves(AlarmRingActivity.intent(context, 1))
        assertResolves(BriefingActivity.dismissAlarmIntent(context, 1))
    }

    @Test fun planRouteRoundTripsItsSegment() {
        assertEquals(PlanSegment.TASKS, PlanRoute.parse(PlanRoute.of(PlanSegment.TASKS).substringAfter("=")))
        assertEquals(PlanSegment.TIMETABLE, PlanRoute.parse(null))
        assertEquals(4, TopLevelDestination.entries.size)
    }
}
