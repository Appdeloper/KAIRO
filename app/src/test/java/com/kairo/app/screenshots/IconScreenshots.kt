package com.kairo.app.screenshots

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairo.app.R
import com.kairo.app.ui.design.KairoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The adaptive launcher icon under the three common launcher masks, plus the themed (monochrome) layer
 * as Android 13+ tints it. An adaptive layer is 108 dp of which the middle 72 dp is shown, hence 120 → 180.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h851dp-xhdpi")
class IconScreenshots {
    @get:Rule val rule = createComposeRule()

    private val masks: List<Pair<String, Shape>> = listOf(
        "Circle" to CircleShape,
        "Squircle" to RoundedCornerShape(38),
        "Square" to RoundedCornerShape(8),
    )

    @Test fun launcherIconMasks() = Shots.capture(rule, "icon_masks") {
        // Grey backdrop so the dark icon's mask edge is visible.
        Column(Modifier.background(Color(0xFF3A3F4B)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            masks.forEach { (name, shape) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    IconLayer(shape, Color(0xFF05070F), R.drawable.ic_launcher_foreground, tint = null)
                    IconLayer(shape, Color(0xFFD6E3FF), R.drawable.ic_launcher_monochrome, tint = Color(0xFF15336E))
                    Text(name, style = KairoTheme.type.labelSmall, softWrap = false)
                }
            }
        }
    }

    @Composable
    private fun IconLayer(shape: Shape, background: Color, layer: Int, tint: Color?) {
        Box(Modifier.size(120.dp).clip(shape).background(background), contentAlignment = Alignment.Center) {
            Image(
                painterResource(layer),
                contentDescription = null,
                modifier = Modifier.requiredSize(180.dp),
                colorFilter = tint?.let { ColorFilter.tint(it) },
            )
        }
    }
}
