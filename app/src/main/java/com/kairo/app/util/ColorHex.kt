package com.kairo.app.util

import androidx.compose.ui.graphics.Color

/** Role colors come from the database, so a malformed value must degrade to gray, not crash a screen. */
fun parseHexColor(hex: String, fallback: Color = Color.Gray): Color = runCatching {
    val clean = hex.removePrefix("#")
    val argb = when (clean.length) {
        6 -> 0xFF000000 or clean.toLong(16)
        8 -> clean.toLong(16)
        else -> return fallback
    }
    Color(argb.toInt())
}.getOrDefault(fallback)
