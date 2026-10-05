package com.kairo.app.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log

/**
 * Plays the alarm on the ALARM stream (USAGE_ALARM), looping, with a volume ramp. USAGE_ALARM is
 * what lets it sound through Do Not Disturb (when alarms are allowed) and, on Android 17, play from
 * the background at all (the exact-alarm exemption in "background audio hardening").
 *
 * Phone calls: the call takes audio focus, so we pause; when focus comes back we resume.
 */
class AlarmPlayer(private val context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var tone: ToneGenerator? = null
    private var focusRequest: AudioFocusRequest? = null
    private var rampStartedAt = 0L
    private var rampSeconds = 0
    private var pausedForFocus = false

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun start(plan: AlarmPlan) {
        rampSeconds = plan.rampUpSeconds
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener(::onFocusChange, handler)
            .build()
        focusRequest = request
        when (audio.requestAudioFocus(request)) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> play(plan)
            // In a call: focus arrives (onFocusChange GAIN) when the call ends. Vibration keeps going meanwhile.
            AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                pendingPlan = plan
                pausedForFocus = true
            }
            // Never stay silent because of focus: play anyway.
            else -> play(plan)
        }
    }

    private var pendingPlan: AlarmPlan? = null

    private fun onFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                pausedForFocus = true
                player?.takeIf { it.isPlaying }?.pause()
                tone?.stopTone()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                pausedForFocus = false
                pendingPlan?.let {
                    pendingPlan = null
                    play(it)
                } ?: player?.start()
            }
        }
    }

    private fun play(plan: AlarmPlan) {
        val candidates = listOfNotNull(
            plan.ringtoneUri?.let(Uri::parse),
            RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM),
            Settings.System.DEFAULT_ALARM_ALERT_URI,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        )
        for (uri in candidates) {
            try {
                player = MediaPlayer().apply {
                    setAudioAttributes(attributes)
                    setDataSource(context, uri)
                    isLooping = true
                    setVolume(START_VOLUME, START_VOLUME)
                    prepare()
                    start()
                }
                startRamp()
                return
            } catch (e: Exception) {
                // Deleted file, locked storage before first unlock, bad URI: try the next one.
                Log.w(TAG, "Ringtone $uri failed, trying next", e)
                player?.release()
                player = null
            }
        }
        // Last resort: a generated beep can't be missing.
        tone = ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME).also { beepLoop(it) }
    }

    private fun startRamp() {
        rampStartedAt = System.currentTimeMillis()
        handler.post(rampStep)
    }

    private val rampStep: Runnable = object : Runnable {
        override fun run() {
            val p = player ?: return
            val fraction = if (rampSeconds <= 0) 1f else ((System.currentTimeMillis() - rampStartedAt) / (rampSeconds * 1000f)).coerceIn(0f, 1f)
            val volume = START_VOLUME + (1f - START_VOLUME) * fraction
            p.setVolume(volume, volume)
            if (fraction < 1f) handler.postDelayed(this, RAMP_STEP_MS)
        }
    }

    private fun beepLoop(generator: ToneGenerator) {
        handler.post(object : Runnable {
            override fun run() {
                if (tone == null) return
                if (!pausedForFocus) generator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, BEEP_MS)
                handler.postDelayed(this, BEEP_MS * 2L)
            }
        })
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        player?.run {
            try {
                stop()
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Player already stopped", e)
            }
            release()
        }
        player = null
        tone?.release()
        tone = null
        pendingPlan = null
        focusRequest?.let(audio::abandonAudioFocusRequest)
        focusRequest = null
    }

    private companion object {
        const val TAG = "KairoAlarm"
        const val START_VOLUME = 0.05f
        const val RAMP_STEP_MS = 250L
        const val BEEP_MS = 600
    }
}
