package com.kairo.app.service.shake

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.kairo.app.KairoApp
import com.kairo.app.data.prefs.ShakeSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/**
 * Listens for a double shake and opens the briefing. Runs as a specialUse foreground service:
 * since Android 9, background apps get no accelerometer events, so a foreground service is the
 * only way to keep listening. Start it only from visible UI (see ShakeControl).
 */
class ShakeService : Service(), SensorEventListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val detector = ShakeDetector()
    private var settings = ShakeSettings()
    private var sensorRegistered = false
    private var foreground = false
    private lateinit var sensors: SensorManager
    private lateinit var launcher: BriefingLauncher

    private val prefs get() = (application as KairoApp).container.shakePrefsRepository

    /** Non-wake-up sensors don't deliver while the phone sleeps; listening with the screen off only wastes power. */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> {
                    registerSensor()
                    scope.launch { prefs.heartbeat(System.currentTimeMillis()) }
                }
                Intent.ACTION_SCREEN_OFF -> unregisterSensor()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        sensors = getSystemService(SensorManager::class.java)
        launcher = BriefingLauncher(this) { path -> scope.launch { prefs.recordLaunchPath(path) } }
        if (!enterForeground()) return
        _running.value = true

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        if (getSystemService(PowerManager::class.java).isInteractive) registerSensor()

        scope.launch {
            prefs.settings.collect {
                settings = it
                detector.threshold = it.threshold
            }
        }
        scope.launch {
            while (true) {
                prefs.heartbeat(System.currentTimeMillis())
                delay(HEARTBEAT_MS)
            }
        }
    }

    /**
     * When Android restarts a sticky service on its own, some versions/ROMs refuse the foreground
     * start. Never crash-loop: stop, and ask the user for one tap instead.
     */
    private fun enterForeground(): Boolean = try {
        ServiceCompat.startForeground(this, ShakeNotifications.STATUS_ID, ShakeNotifications.status(this), foregroundType())
        foreground = true
        true
    } catch (e: IllegalStateException) {
        Log.w(BriefingLauncher.TAG, "foreground start refused; asking the user to re-arm", e)
        ShakeNotifications.postRearm(this)
        stopSelf()
        false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!foreground) return START_NOT_STICKY
        if (intent?.action == ACTION_STOP) {
            scope.launch {
                prefs.setEnabled(false)
                stopSelf()
            }
            return START_NOT_STICKY
        }
        // A null intent means Android killed us and restarted the sticky service: count it.
        if (intent == null) scope.launch { prefs.recordRestart(LocalDate.now().toEpochDay()) }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerSensor() {
        if (sensorRegistered) return
        val accelerometer = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sensorRegistered = sensors.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
    }

    private fun unregisterSensor() {
        if (!sensorRegistered) return
        sensors.unregisterListener(this)
        sensorRegistered = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        val timeMs = event.timestamp / NANOS_PER_MILLI
        if (detector.onSample(event.values[0], event.values[1], event.values[2], timeMs)) onShake()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    /** Active hours and the charging rule are checked only when a gesture fires, so idle cost stays flat. */
    private fun onShake() {
        val now = LocalTime.now()
        val inHours = ActiveHours.isActive(now.hour * 60 + now.minute, settings.activeStartMinute, settings.activeEndMinute)
        val chargingOk = !settings.onlyWhileCharging || getSystemService(BatteryManager::class.java).isCharging
        if (suppressLaunch) {
            Log.i(BriefingLauncher.TAG, "shake detected while the Settings test meter is open; not launching")
            return
        }
        if (!inHours || !chargingOk) {
            Log.i(BriefingLauncher.TAG, "shake ignored (activeHours=$inHours, chargingOk=$chargingOk)")
            return
        }
        tick()
        launcher.launch()
    }

    private fun tick() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
        } else {
            VibrationEffect.createOneShot(TICK_MS, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        vibrator.vibrate(effect)
    }

    override fun onDestroy() {
        _running.value = false
        unregisterSensor()
        if (foreground) unregisterReceiver(screenReceiver)
        scope.cancel()
        super.onDestroy()
    }

    private fun foregroundType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0

    companion object {
        private const val ACTION_STOP = "com.kairo.app.shake.STOP"
        private const val HEARTBEAT_MS = 60_000L
        private const val NANOS_PER_MILLI = 1_000_000L
        private const val TICK_MS = 20L

        private val _running = MutableStateFlow(false)

        /** Set while the Settings test meter is on screen, so testing sensitivity doesn't open the briefing. */
        @Volatile var suppressLaunch = false

        /** True while the service lives in this process: the most honest "is shake armed?" signal. */
        val running: StateFlow<Boolean> = _running.asStateFlow()

        fun startIntent(context: Context) = Intent(context, ShakeService::class.java)
        fun stopIntent(context: Context): Intent = Intent(context, ShakeService::class.java).setAction(ACTION_STOP)
    }
}
