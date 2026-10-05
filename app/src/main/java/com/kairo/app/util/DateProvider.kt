package com.kairo.app.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.LocalDate
import java.time.LocalTime

interface DateProvider {
    fun today(): LocalDate
    fun nowMinuteOfDay(): Int

    /** Re-emits after midnight so a screen left open overnight moves to the new day. */
    fun todayFlow(): Flow<LocalDate> = flow {
        while (true) {
            emit(today())
            delay(TICK_MILLIS)
        }
    }.distinctUntilChanged()

    private companion object {
        const val TICK_MILLIS = 60_000L
    }
}

object SystemDateProvider : DateProvider {
    override fun today(): LocalDate = LocalDate.now()
    override fun nowMinuteOfDay(): Int = LocalTime.now().let { it.hour * 60 + it.minute }
}
