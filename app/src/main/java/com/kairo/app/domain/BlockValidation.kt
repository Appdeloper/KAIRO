package com.kairo.app.domain

enum class BlockError { EMPTY_TITLE, END_NOT_AFTER_START, BAD_DAY }

object BlockValidation {
    /** Blocks may not cross midnight in v1: a lecture never does, and it keeps per-day queries simple. */
    fun validate(title: String, dayOfWeek: Int, startMinute: Int, endMinute: Int): BlockError? = when {
        title.isBlank() -> BlockError.EMPTY_TITLE
        dayOfWeek !in 1..7 -> BlockError.BAD_DAY
        endMinute <= startMinute -> BlockError.END_NOT_AFTER_START
        else -> null
    }
}
