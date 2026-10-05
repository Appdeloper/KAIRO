package com.kairo.app.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.kairo.app.KairoApp
import com.kairo.app.R
import com.kairo.app.domain.TimelineBuilder
import com.kairo.app.domain.brief.Upcoming
import com.kairo.app.ui.briefing.BriefingActivity

/** 2x2 home-screen widget: a small orb and the next thing on today's plan. Tap opens the briefing. */
class KairoWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val line = nextLine(context)
        provideContent { WidgetBody(line) }
    }

    /** Computed when the widget is refreshed (plan change or every 15 min), so minutes are approximate. */
    private suspend fun nextLine(context: Context): String {
        val container = (context.applicationContext as KairoApp).container
        val state = container.planRepository.loadState()
        val entries = TimelineBuilder.build(state.blocksOn(state.today), state.tasksOn(state.today), state.roles)
        val next = Upcoming.next(entries, state.nowMinute) ?: return context.getString(R.string.widget_all_clear)
        return when {
            next.minutesUntil <= 0 -> context.getString(R.string.widget_now, next.entry.title)
            next.minutesUntil >= 60 -> context.getString(R.string.widget_next_hours, next.entry.title, next.minutesUntil / 60, next.minutesUntil % 60)
            else -> context.getString(R.string.widget_next_minutes, next.entry.title, next.minutesUntil)
        }
    }
}

@Composable
private fun WidgetBody(line: String) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(24.dp)
            .background(ColorProvider(Color(0xFF0F0F16)))
            .clickable(actionStartActivity<BriefingActivity>())
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(provider = ImageProvider(R.drawable.widget_orb), contentDescription = null, modifier = GlanceModifier.size(56.dp))
        Spacer(GlanceModifier.height(8.dp))
        Text(
            text = line,
            maxLines = 2,
            style = TextStyle(color = ColorProvider(Color(0xFFECECF4)), fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
        )
    }
}

class KairoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = KairoWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshWorker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetRefreshWorker.cancel(context)
    }
}
