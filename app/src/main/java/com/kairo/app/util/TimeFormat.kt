package com.kairo.app.util

import android.content.Context
import android.text.format.DateFormat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

fun minuteToLocalTime(minuteOfDay: Int): LocalTime =
    LocalTime.of((minuteOfDay / 60) % 24, minuteOfDay % 60)

/** Honors the device's 12/24h setting instead of forcing one style on the user. */
fun formatMinuteOfDay(context: Context, minuteOfDay: Int): String =
    formatMinuteOfDay(minuteOfDay, DateFormat.is24HourFormat(context))

fun formatMinuteOfDay(minuteOfDay: Int, use24Hour: Boolean, locale: Locale = Locale.getDefault()): String {
    val pattern = if (use24Hour) "HH:mm" else "h:mm a"
    return minuteToLocalTime(minuteOfDay).format(DateTimeFormatter.ofPattern(pattern, locale))
}
