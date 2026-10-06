package com.kairo.app.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG 2.2 AA: normal text needs 4.5:1, large text and icons 3:1. Every foreground role is checked
 * against every surface it can sit on. docs/DESIGN_SYSTEM.md lists the actual ratios.
 */
class ContrastTest {
    private val c = DarkColorRoles
    private val surfaces = mapOf(
        "background" to c.background, "surface1" to c.surface1, "surface2" to c.surface2, "surface3" to c.surface3,
    )

    private fun ratio(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return maxOf(la, lb) / minOf(la, lb)
    }

    private fun assertAll(name: String, fg: Color, min: Double, on: Map<String, Color> = surfaces) {
        on.forEach { (bgName, bg) ->
            val r = ratio(fg, bg)
            assertTrue("$name on $bgName is %.2f, needs %.1f".format(r, min), r >= min)
        }
    }

    @Test fun bodyTextRolesPassAaOnEverySurface() {
        assertAll("textPrimary", c.textPrimary, 4.5)
        assertAll("textSecondary", c.textSecondary, 4.5)
        assertAll("textTertiary", c.textTertiary, 4.5)
    }

    @Test fun accentTextRolesPassAa() {
        listOf("primary" to c.primary, "success" to c.success, "warning" to c.warning, "error" to c.error, "moment" to c.moment)
            .forEach { (n, col) -> assertAll(n, col, 4.5) }
    }

    @Test fun laneColoursPassAaAsTextAndOnTheirTints() {
        Lane.entries.forEach { lane ->
            assertAll(lane.name, lane.color, 4.5)
            val tint = c.tint(lane.color, 0.18f)
            assertTrue("textPrimary on ${lane.name} tint", ratio(c.textPrimary, tint) >= 4.5)
            assertTrue("textSecondary on ${lane.name} tint", ratio(c.textSecondary, tint) >= 4.5)
        }
    }

    @Test fun gradientEndIsOnlyForLargeTextAndIcons() {
        assertAll("primaryEnd (large text / icons)", c.primaryEnd, 3.0)
    }

    @Test fun labelOnPrimaryButtonPassesAcrossTheWholeGradient() {
        assertTrue(ratio(c.onPrimary, c.primary) >= 4.5)
        assertTrue(ratio(c.onPrimary, c.primaryEnd) >= 4.5)
    }

    @Test fun bannerTintsKeepTextReadable() {
        listOf(c.primary, c.warning, c.error, c.success, c.moment).forEach { accent ->
            val tint = c.tint(accent, 0.16f)
            assertTrue(ratio(c.textPrimary, tint) >= 4.5)
            assertTrue(ratio(c.textSecondary, tint) >= 4.5)
            assertTrue("accent icon on its own tint", ratio(accent, tint) >= 3.0)
        }
    }
}
