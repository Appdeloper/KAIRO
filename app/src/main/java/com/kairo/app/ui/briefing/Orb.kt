package com.kairo.app.ui.briefing

import android.graphics.RuntimeShader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kairo.app.ui.theme.KairoColors
import com.kairo.app.ui.theme.KairoTheme
import kotlin.math.sin

enum class OrbState { IDLE, LISTENING, THINKING, DONE }

/** How each state drives the orb; the orb tweens between these so state changes never jump. */
private data class OrbStyle(val breath: Float, val swirl: Float, val bloom: Float, val levelGain: Float)

private fun styleFor(state: OrbState) = when (state) {
    OrbState.IDLE -> OrbStyle(breath = 1f, swirl = 0f, bloom = 0f, levelGain = 0f)
    OrbState.LISTENING -> OrbStyle(breath = 0.3f, swirl = 0.15f, bloom = 0f, levelGain = 1f)
    OrbState.THINKING -> OrbStyle(breath = 0.2f, swirl = 1f, bloom = 0f, levelGain = 0f)
    OrbState.DONE -> OrbStyle(breath = 0.5f, swirl = 0f, bloom = 1f, levelGain = 0f)
}

private const val TAG = "KairoOrb"
private const val TRANSITION_MS = 450

/**
 * Seconds since the clock started, advancing only while [running]. When the activity leaves the
 * resumed state the frame loop is cancelled, so the orb stops costing GPU time in the background.
 */
@Composable
fun rememberAnimationClock(running: Boolean, label: String): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running) {
        if (!running) {
            Log.d(TAG, "$label clock paused")
            return@LaunchedEffect
        }
        Log.d(TAG, "$label clock running")
        val base = time.floatValue
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> time.floatValue = base + (now - start) / NANOS_PER_SECOND }
        }
    }
    return time
}

private const val NANOS_PER_SECOND = 1_000_000_000f

@Composable
fun Orb(
    state: OrbState,
    level: Float,
    running: Boolean,
    modifier: Modifier = Modifier,
    forceFallback: Boolean = false,
) {
    val target = styleFor(state)
    val breath by animateFloatAsState(target.breath, tween(TRANSITION_MS), label = "breath")
    val swirl by animateFloatAsState(target.swirl, tween(TRANSITION_MS), label = "swirl")
    val bloom by animateFloatAsState(target.bloom, tween(TRANSITION_MS), label = "bloom")
    val gain by animateFloatAsState(target.levelGain, tween(TRANSITION_MS), label = "gain")
    val smoothLevel by animateFloatAsState(level * gain, tween(durationMillis = 90), label = "level")
    val time by rememberAnimationClock(running, "orb")

    val shader = remember(forceFallback) {
        if (!forceFallback && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) OrbShader.createOrNull() else null
    }
    Canvas(modifier) {
        val params = OrbParams(time, breath, swirl, bloom, smoothLevel)
        if (shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            shader.draw(this, params)
        } else {
            drawFallbackOrb(params)
        }
    }
}

private data class OrbParams(val time: Float, val breath: Float, val swirl: Float, val bloom: Float, val level: Float)

/** Below API 33 (or if the shader fails to compile): layered radial gradients and a rotating ring. */
private fun DrawScope.drawFallbackOrb(p: OrbParams) {
    val base = size.minDimension * 0.30f
    val radius = base * (1f + 0.05f * sin(p.time * 1.2f) * p.breath + 0.25f * p.level + 0.3f * p.bloom)
    drawCircle(
        brush = Brush.radialGradient(
            0f to KairoColors.NeonCyan.copy(alpha = 0.35f),
            1f to Color.Transparent,
            center = center,
            radius = radius * 1.9f,
        ),
        radius = radius * 1.9f,
    )
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.White,
            0.35f to KairoColors.NeonCyan,
            1f to KairoColors.NeonMagenta,
            center = center,
            radius = radius,
        ),
        radius = radius,
    )
    if (p.swirl > 0.01f) {
        rotate(degrees = p.time * 240f) {
            drawArc(
                brush = Brush.sweepGradient(listOf(Color.Transparent, KairoColors.NeonLime.copy(alpha = p.swirl), Color.Transparent), center),
                startAngle = 0f,
                sweepAngle = 300f,
                useCenter = false,
                topLeft = Offset(center.x - radius * 1.25f, center.y - radius * 1.25f),
                size = androidx.compose.ui.geometry.Size(radius * 2.5f, radius * 2.5f),
                style = Stroke(width = radius * 0.12f),
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class OrbShader private constructor(private val shader: RuntimeShader) {
    private val brush = ShaderBrush(shader)

    fun draw(scope: DrawScope, p: OrbParams) = with(scope) {
        shader.setFloatUniform("iResolution", size.width, size.height)
        shader.setFloatUniform("iTime", p.time)
        shader.setFloatUniform("breath", p.breath)
        shader.setFloatUniform("swirl", p.swirl)
        shader.setFloatUniform("bloom", p.bloom)
        shader.setFloatUniform("level", p.level)
        drawRect(brush)
    }

    companion object {
        /** AGSL is compiled on the device; any compile error falls back to the Canvas orb, never a crash. */
        fun createOrNull(): OrbShader? = try {
            OrbShader(
                RuntimeShader(ORB_AGSL).apply {
                    setColorUniform("coreColor", KairoColors.NeonCyan.toArgb())
                    setColorUniform("edgeColor", KairoColors.NeonMagenta.toArgb())
                },
            )
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "AGSL orb unavailable, using Canvas fallback", e)
            null
        }
    }
}

private const val ORB_AGSL = """
uniform float2 iResolution;
uniform float iTime;
uniform float breath;
uniform float swirl;
uniform float bloom;
uniform float level;
layout(color) uniform half4 coreColor;
layout(color) uniform half4 edgeColor;

half4 main(float2 fragCoord) {
    float2 uv = (fragCoord - 0.5 * iResolution) / min(iResolution.x, iResolution.y);
    float r = length(uv);
    float a = atan(uv.y, uv.x);
    float radius = 0.28 + 0.02 * sin(iTime * 1.2) * breath + 0.07 * level + 0.09 * bloom;
    float wobble = 0.012 * sin(a * 6.0 + iTime * 3.0) * (swirl + level);
    float d = r - radius - wobble;
    float body = smoothstep(0.015, -0.015, d);
    float glow = exp(-max(d, 0.0) * (9.0 - 4.0 * bloom)) * 0.55;
    float spiral = 0.5 + 0.5 * sin(a * 3.0 - r * 22.0 + iTime * 5.0);
    float t = clamp(r / radius + 0.25 * swirl * (spiral - 0.5), 0.0, 1.0);
    float3 col = mix(float3(coreColor.rgb), float3(edgeColor.rgb), t);
    col = mix(col, float3(1.0), (1.0 - smoothstep(0.0, radius * 0.6, r)) * 0.55);
    float alpha = clamp(body + glow, 0.0, 1.0);
    return half4(half3(col * alpha), half(alpha));
}
"""

@Preview(showBackground = true, backgroundColor = 0xFF07070B)
@Composable
private fun OrbStatesPreview() {
    KairoTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OrbState.entries.forEach { Orb(it, level = 0.6f, running = false, forceFallback = true, modifier = Modifier.size(90.dp)) }
        }
    }
}

@Preview(name = "Orb idle", showBackground = true, backgroundColor = 0xFF07070B)
@Composable
private fun OrbIdlePreview() {
    KairoTheme { Orb(OrbState.IDLE, 0f, running = false, forceFallback = true, modifier = Modifier.size(180.dp)) }
}

@Preview(name = "Orb listening", showBackground = true, backgroundColor = 0xFF07070B)
@Composable
private fun OrbListeningPreview() {
    KairoTheme { Orb(OrbState.LISTENING, 0.8f, running = false, forceFallback = true, modifier = Modifier.size(180.dp)) }
}

@Preview(name = "Orb thinking", showBackground = true, backgroundColor = 0xFF07070B)
@Composable
private fun OrbThinkingPreview() {
    KairoTheme { Orb(OrbState.THINKING, 0f, running = false, forceFallback = true, modifier = Modifier.size(180.dp)) }
}

@Preview(name = "Orb done", showBackground = true, backgroundColor = 0xFF07070B)
@Composable
private fun OrbDonePreview() {
    KairoTheme { Orb(OrbState.DONE, 0f, running = false, forceFallback = true, modifier = Modifier.size(180.dp)) }
}
