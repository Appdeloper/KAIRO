package com.kairo.app.ai.speech

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** A recognised phrase; partial ones stream in while the user is still talking. */
data class Transcript(val text: String, val isFinal: Boolean)

enum class SpeechError { NO_MATCH, NO_PERMISSION, BUSY, OTHER }

/**
 * Speech-to-text behind an interface so Phase 2 can add other engines. Implementations must only
 * be started from a visible activity (CLAUDE.md rule 6).
 */
interface SpeechProvider {
    val results: Flow<Transcript>
    val errors: Flow<SpeechError>

    /** Normalised input level 0..1, for the orb's listening pulse. */
    val level: StateFlow<Float>
    val listening: StateFlow<Boolean>
    val isAvailable: Boolean

    fun start()
    fun stop()

    /** Drops the current session without delivering a result (used when the activity pauses). */
    fun cancel()
    fun release()
}
