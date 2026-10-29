package com.leanbitlab.lwidget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class StepCounterService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    
    private lateinit var prefs: SharedPreferences
    private var lastTotalSteps: Float = 0f
    private var baselineSteps: Float = 0f
    private var stepDate: String = ""
    private var isReceiverRegistered = false

    private var lastBroadcastSteps: Float = 0f
    private var lastBroadcastTimeMs: Long = 0L
    private var hasPendingUpdate: Boolean = false
    private var lastBatteryPct: Int = -1
    private var lastBatteryTemp: Int = -1
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val flushRunnable = Runnable {
        flushStepUpdate()
    }

    private val updateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
                    val pct = if (scale > 0) (level * 100 / scale.toFloat()).toInt() else -1
                    val temp = intent.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, -1)
                    val tempTenths = if (temp != -1) temp / 10 else -1

                    // Only broadcast if battery percentage or integer temperature actually changed
                    if (pct != lastBatteryPct || tempTenths != lastBatteryTemp) {
                        lastBatteryPct = pct
                        lastBatteryTemp = tempTenths
                        val updateIntent = Intent(context, AwidgetProvider::class.java).apply {
                            this.action = AwidgetProvider.ACTION_BATTERY_UPDATE
                        }
                        context.sendBroadcast(updateIntent)
                    }
                }
                Intent.ACTION_TIME_TICK -> {
                    val updateIntent = Intent(context, AwidgetProvider::class.java).apply {
                        this.action = AwidgetProvider.ACTION_BATTERY_UPDATE
                    }
                    context.sendBroadcast(updateIntent)
                }
                Intent.ACTION_SCREEN_ON -> {
                    // Instantly sync step count when user turns on the screen
                    if (hasPendingUpdate || lastBroadcastSteps != lastTotalSteps) {
                        flushStepUpdate()
                    }
                }
            }
        }
    }
    
    companion object {
        const val CHANNEL_ID = "StepCounterChannel"
        const val NOTIFICATION_ID = 42100
        const val ACTION_STEP_UPDATE = "com.leanbitlab.lwidget.ACTION_STEP_UPDATE"

        fun isStepOrKeepAliveEnabled(context: Context): Boolean {
            val globalPrefs = context.getSharedPreferences("com.leanbitlab.lwidget.PREFS", Context.MODE_PRIVATE)
            if (globalPrefs.getBoolean("show_steps", false) || globalPrefs.getBoolean("keep_alive", false)) {
                return true
            }
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, AwidgetProvider::class.java))
            for (id in ids) {
                val wPrefs = context.getSharedPreferences("com.leanbitlab.lwidget.PREFS_$id", Context.MODE_PRIVATE)
                if (wPrefs.getBoolean("show_steps", false) || wPrefs.getBoolean("keep_alive", false)) {
                    return true
                }
            }
            return false
        }

        fun isStepsVisibleAnywhere(context: Context): Boolean {
            val globalPrefs = context.getSharedPreferences("com.leanbitlab.lwidget.PREFS", Context.MODE_PRIVATE)
            if (globalPrefs.getBoolean("show_steps", false)) {
                return true
            }
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, AwidgetProvider::class.java))
            for (id in ids) {
                val wPrefs = context.getSharedPreferences("com.leanbitlab.lwidget.PREFS_$id", Context.MODE_PRIVATE)
                if (wPrefs.getBoolean("show_steps", false)) {
                    return true
                }
            }
            return false
        }

        fun startServiceIfEnabled(context: Context) {
            if (!isStepOrKeepAliveEnabled(context)) return

            val hasActivityPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACTIVITY_RECOGNITION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
            if (!hasActivityPerm) return

            try {
                val serviceIntent = Intent(context, StepCounterService::class.java)
                ContextCompat.startForegroundService(context, serviceIntent)
            } catch (e: Exception) {
                android.util.Log.e("StepCounterService", "Failed to start StepCounterService", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        prefs = getSharedPreferences("com.leanbitlab.lwidget.PREFS", Context.MODE_PRIVATE)
        lastTotalSteps = prefs.getFloat("last_total_steps", 0f)
        baselineSteps = prefs.getFloat("step_baseline", 0f)
        stepDate = prefs.getString("step_date", "") ?: ""

        createNotificationChannel()

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, createNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
            } else {
                startForeground(NOTIFICATION_ID, createNotification())
            }
        } catch (e: Exception) {
            android.util.Log.e("LWidget", "Cannot start foreground service", e)
            stopSelf()
            return
        }
        
        val showSteps = isStepsVisibleAnywhere(this)
        val isNeeded = isStepOrKeepAliveEnabled(this)
        
        // If steps is explicitly enabled, require ACTIVITY_RECOGNITION
        val hasActivityPerm = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACTIVITY_RECOGNITION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (showSteps && !hasActivityPerm) {
            val keepAlive = prefs.getBoolean("keep_alive", false)
            if (!keepAlive) {
                stopSelf()
                return
            }
        }
        if (!isNeeded) {
            stopSelf()
            return
        }

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        
        // Always register the step sensor if available and permission is granted
        if (hasActivityPerm) {
            stepSensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
        
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_CONFIGURATION_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            isReceiverRegistered = true
        } else {
            registerReceiver(updateReceiver, filter)
            isReceiverRegistered = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, createNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
            } else {
                startForeground(NOTIFICATION_ID, createNotification())
            }
        } catch (e: Exception) {
            android.util.Log.e("StepCounterService", "Cannot start foreground in onStartCommand", e)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(flushRunnable)
        if (hasPendingUpdate || lastBroadcastSteps != lastTotalSteps) {
            flushStepUpdate()
        }
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(updateReceiver)
                isReceiverRegistered = false
            } catch (e: Exception) {
                android.util.Log.e("LWidget", "Error unregistering receiver", e)
            }
        }
        if (::sensorManager.isInitialized) {
            sensorManager.unregisterListener(this)
        }
        super.onDestroy()
    }

    private fun flushStepUpdate() {
        mainHandler.removeCallbacks(flushRunnable)
        hasPendingUpdate = false
        lastBroadcastSteps = lastTotalSteps
        lastBroadcastTimeMs = android.os.SystemClock.elapsedRealtime()

        val updateIntent = Intent(this, AwidgetProvider::class.java).apply {
            action = ACTION_STEP_UPDATE
        }
        sendBroadcast(updateIntent)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        
        val totalSteps = event.values[0]

        // Daily reset and reboot handling
        val today = java.time.LocalDate.now().toString()
        val isNewDay = stepDate != today
        var isHardwareReboot = false

        if (isNewDay) {
            stepDate = today
            baselineSteps = totalSteps
            lastTotalSteps = totalSteps
            prefs.edit()
                .putString("step_date", today)
                .putFloat("step_baseline", totalSteps)
                .putFloat("last_total_steps", totalSteps)
                .apply()
        } else {
            // Hardware rebooted and reset the total steps counter
            isHardwareReboot = totalSteps < lastTotalSteps
            if (isHardwareReboot) {
                val stepsWalkedTodayBeforeReboot = (lastTotalSteps - baselineSteps).coerceAtLeast(0f)
                baselineSteps = totalSteps - stepsWalkedTodayBeforeReboot
                prefs.edit().putFloat("step_baseline", baselineSteps).apply()
            }
            lastTotalSteps = totalSteps
            prefs.edit().putFloat("last_total_steps", totalSteps).apply()
        }

        hasPendingUpdate = true

        // If daily reset or reboot happened, notify widget immediately
        if (isHardwareReboot || isNewDay) {
            flushStepUpdate()
            return
        }

        // Screen-off check (Pocket mode): do not wake up system/widget if screen is off
        val powerManager = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        val isInteractive = powerManager?.isInteractive != false
        if (!isInteractive) {
            // Keep pending update; will flush immediately on ACTION_SCREEN_ON
            return
        }

        // Screen is on: throttle to at most once per 10 seconds or 15 steps
        val now = android.os.SystemClock.elapsedRealtime()
        val timeSinceLast = now - lastBroadcastTimeMs
        val stepsSinceLast = totalSteps - lastBroadcastSteps
        val THROTTLE_MS = 10_000L
        val STEP_THRESHOLD = 15f

        if (timeSinceLast >= THROTTLE_MS || stepsSinceLast >= STEP_THRESHOLD) {
            flushStepUpdate()
        } else {
            // Guarantee trailing edge update so steps are never lost when user stops walking
            mainHandler.removeCallbacks(flushRunnable)
            mainHandler.postDelayed(flushRunnable, THROTTLE_MS - timeSinceLast)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used
    }

    private fun createNotification(): Notification {
        val settingsIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, settingsIntent, PendingIntent.FLAG_IMMUTABLE)

        val showSteps = isStepsVisibleAnywhere(this)
        
        val title = if (showSteps) "Lwidget Step Counter" else "Lwidget Keep Alive"
        val text = if (showSteps) "Counting steps in the background" else "Keeping widget updated"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_steps) 
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            
        // Use a less intrusive priority
        builder.setPriority(NotificationCompat.PRIORITY_MIN)
            
        return builder.build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Step Counter Service",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Keeps the step counter running in the background."
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
