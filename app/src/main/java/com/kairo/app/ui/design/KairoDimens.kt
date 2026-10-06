package com.kairo.app.ui.design

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** 4/8 dp grid. Use these instead of literal dp in screens. */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp

    /** Side margin of every screen. */
    val screen = 20.dp

    /** Clear space above the bottom bar + floating orb button. */
    val bottomBarClearance = 120.dp
}

object Radius {
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val small = RoundedCornerShape(sm)
    val medium = RoundedCornerShape(md)
    val large = RoundedCornerShape(lg)
    val full = CircleShape
}

object Stroke {
    val hairline = 1.dp
    val regular = 1.5.dp
    val strong = 2.dp
    val ring = 8.dp
}

/**
 * Elevation is light, not shadow: higher glass levels are lighter navy with a brighter top edge and,
 * at the top level, a soft primary glow.
 */
enum class Elevation(val highlightAlpha: Float, val glowAlpha: Float) {
    FLAT(0f, 0f),
    LOW(0.06f, 0f),
    RAISED(0.10f, 0f),
    GLOW(0.14f, 0.16f),
}

/** Minimum touch target (Material and WCAG 2.5.8 guidance). */
val MinTouchTarget = 48.dp
