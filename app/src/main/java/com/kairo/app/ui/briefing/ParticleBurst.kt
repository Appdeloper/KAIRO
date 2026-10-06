package com.kairo.app.ui.briefing

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.kairo.app.ui.design.Palette
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** One spark: direction and speed relative to the burst's size, plus which neon it uses. */
data class Particle(val angle: Float, val speed: Float, val radius: Float, val colorIndex: Int)

object ParticleField {
    /** Hard cap so a burst stays cheap on low-end phones, whatever the caller asks for. */
    const val MAX_PARTICLES = 120
    const val DEFAULT_COUNT = 90
    const val DURATION_MS = 900L

    fun spawn(count: Int, seed: Int): List<Particle> {
        val random = Random(seed)
        return List(count.coerceIn(0, MAX_PARTICLES)) {
            Particle(
                angle = random.nextFloat() * TWO_PI,
                speed = 0.25f + random.nextFloat() * 0.55f,
                radius = 2f + random.nextFloat() * 4f,
                colorIndex = random.nextInt(PALETTE_SIZE),
            )
        }
    }

    const val PALETTE_SIZE = 3
    private const val TWO_PI = (Math.PI * 2).toFloat()
}

private val palette = listOf(Palette.Cyan, Palette.Blue, Palette.SoftWhite)

/**
 * Plays a burst each time [trigger] changes (0 = never). Only animates while [running]; if the
 * activity pauses mid-burst, the burst is simply dropped.
 */
@Composable
fun ParticleBurst(trigger: Int, running: Boolean, modifier: Modifier = Modifier) {
    if (trigger == 0) return
    val particles = remember(trigger) { ParticleField.spawn(ParticleField.DEFAULT_COUNT, seed = trigger) }
    var progress by remember(trigger) { mutableFloatStateOf(0f) }
    LaunchedEffect(trigger, running) {
        if (!running) {
            progress = 1f
            return@LaunchedEffect
        }
        val start = withFrameNanos { it }
        while (progress < 1f) {
            withFrameNanos { now -> progress = ((now - start) / 1_000_000f / ParticleField.DURATION_MS).coerceAtMost(1f) }
        }
    }
    if (progress >= 1f) return
    Canvas(modifier) {
        val reach = size.minDimension * 0.6f
        val gravity = size.minDimension * 0.25f * progress * progress
        particles.forEach { p ->
            val distance = reach * p.speed * progress
            drawCircle(
                color = palette[p.colorIndex].copy(alpha = 1f - progress),
                radius = p.radius * (1f - progress * 0.5f) * density,
                center = Offset(center.x + cos(p.angle) * distance, center.y + sin(p.angle) * distance + gravity),
            )
        }
    }
}

