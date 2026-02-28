package com.leanbitlab.lwidget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
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
    
    private val updateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action == Intent.ACTION_BATTERY_CHANGED || action == Intent.ACTION_TIME_TICK) {
                val updateIntent = Intent(context, AwidgetProvider::class.java).apply {
                    this.action = AwidgetProvider.ACTION_BATTERY_UPDATE
                }
                context.sendBroadcast(updateIntent)
            }
        }
    }
    
    companion object {
        const val CHANNEL_ID = "StepCounterChannel"
        const val NOTIFICATION_ID = 42100
        const val ACTION_STEP_UPDATE = "com.leanbitlab.lwidget.ACTION_STEP_UPDATE"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACTIVITY_RECOGNITION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, createNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        
        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_TIME_TICK)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(updateReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Sticky so it restarts if killed
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        unregisterReceiver(updateReceiver)
        sensorManager.unregisterListener(this)
        super.onDestroy()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        
        val totalSteps = event.values[0]
        val prefs = getSharedPreferences("com.leanbitlab.lwidget.PREFS", Context.MODE_PRIVATE)
        val lastTotalSteps = prefs.getFloat("last_total_steps", 0f)
        var baselineSteps = prefs.getFloat("step_baseline", 0f)

        // Hardware rebooted and reset the total steps to 0
        if (totalSteps < lastTotalSteps) {
            baselineSteps = totalSteps - (lastTotalSteps - baselineSteps)
            prefs.edit().putFloat("step_baseline", baselineSteps).apply()
        }
        
        prefs.edit().putFloat("last_total_steps", totalSteps).apply()

        // Daily reset logic
        val today = java.time.LocalDate.now().toString()
        val savedDate = prefs.getString("step_date", "")

        if (savedDate != today) {
            prefs.edit()
                .putString("step_date", today)
                .putFloat("step_baseline", totalSteps)
                .apply()
        }
        
        // Notify widget provider to update
        val updateIntent = Intent(this, AwidgetProvider::class.java).apply {
            action = ACTION_STEP_UPDATE
        }
        sendBroadcast(updateIntent)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used
    }

    private fun createNotification(): Notification {
        val settingsIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, settingsIntent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Lwidget Step Counter")
            .setContentText("Listening for steps in the background")
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
