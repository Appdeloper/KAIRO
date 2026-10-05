package com.kairo.app.ui.briefing

import android.content.Context
import com.kairo.app.R
import com.kairo.app.domain.DayPart
import com.kairo.app.domain.brief.BriefStrings
import com.kairo.app.util.formatMinuteOfDay

/** Local briefing wording from strings.xml, so it's translatable like the rest of the UI. */
class ResourceBriefStrings(context: Context) : BriefStrings {
    private val res = context.applicationContext

    override fun greeting(dayPart: DayPart, firstName: String): String = res.getString(
        when (dayPart) {
            DayPart.MORNING -> R.string.greeting_morning
            DayPart.AFTERNOON -> R.string.greeting_afternoon
            DayPart.EVENING -> R.string.greeting_evening
            DayPart.NIGHT -> R.string.greeting_night
        },
        firstName,
    )

    override fun emptyDay(dayPart: DayPart): String = res.getString(
        if (dayPart == DayPart.EVENING || dayPart == DayPart.NIGHT) R.string.brief_empty_evening else R.string.brief_empty_day,
    )

    override fun summary(remainingCount: Int, nextTitle: String, nextStartMinute: Int, minutesUntil: Int): String =
        res.resources.getQuantityString(
            R.plurals.brief_summary,
            remainingCount,
            remainingCount,
            nextTitle,
            formatMinuteOfDay(res, nextStartMinute),
            relative(minutesUntil),
        )

    override fun gapSuggestion(gapMinutes: Int): String = res.getString(R.string.brief_gap_suggestion, duration(gapMinutes))

    override fun ifThen(nextTitle: String): String = res.getString(R.string.brief_if_then, nextTitle)

    private fun relative(minutes: Int): String = when {
        minutes <= 0 -> res.getString(R.string.brief_now)
        else -> res.getString(R.string.brief_in, duration(minutes))
    }

    private fun duration(minutes: Int): String = if (minutes >= 60) {
        res.getString(R.string.duration_hours_minutes, minutes / 60, minutes % 60)
    } else {
        res.getString(R.string.duration_minutes, minutes)
    }
}
