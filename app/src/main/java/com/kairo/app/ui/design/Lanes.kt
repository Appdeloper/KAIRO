package com.kairo.app.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.kairo.app.data.local.Role
import com.kairo.app.util.parseHexColor

/**
 * The four life lanes. Each colour is distinct from the others, from the primary cyan and from amber,
 * and each has a muted tint for backgrounds. Roles are matched by their stored colour (old and new
 * defaults), so a renamed lane keeps its colour; a custom colour is used as-is.
 */
enum class Lane(val color: Color, val legacyHex: Set<String>) {
    COLLEGE(Color(0xFF38D9F5), setOf("#00E5FF", "#38D9F5")),
    INTERN(Color(0xFF9B8CFF), setOf("#B388FF", "#9B8CFF")),
    CLIENT(Color(0xFF3EE8A8), setOf("#39FF88", "#3EE8A8")),
    CONTENT(Color(0xFFFF6FB7), setOf("#FF2E93", "#FF6FB7")),
    ;

    val hex: String get() = legacyHex.last()

    companion object {
        fun of(role: Role?): Lane? = role?.colorHex?.uppercase()?.let { hex -> entries.firstOrNull { hex in it.legacyHex } }
    }
}

@Immutable
data class LaneStyle(val color: Color, val tint: Color)

/** Colour and muted background for a role, or a neutral style when there's no role. */
@Composable
fun laneStyle(role: Role?): LaneStyle {
    val colors = KairoTheme.colors
    val color = Lane.of(role)?.color ?: role?.let { parseHexColor(it.colorHex, colors.textTertiary) } ?: colors.textTertiary
    return LaneStyle(color = color, tint = colors.tint(color, 0.18f))
}
