/*
 * Copyright (C) 2026 LeanBitLab
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.leanbitlab.lwidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.os.Build
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.app.usage.NetworkStatsManager
import android.os.BatteryManager
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.widget.RemoteViews
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class UpdateMode {
    FULL, TICK, CALENDAR_ONLY, TASKS_ONLY, ALARM_ONLY
}

class AwidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (appWidgetId in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, appWidgetId, UpdateMode.FULL)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleWork(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelWork(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val thisAppWidget = ComponentName(context, AwidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget)

        if (intent.action in listOf(
            Intent.ACTION_BOOT_COMPLETED,
            ACTION_BATTERY_UPDATE,
            StepCounterService.ACTION_STEP_UPDATE,
            Intent.ACTION_PROVIDER_CHANGED,
            android.app.AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED
        )) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    when (intent.action) {
                        Intent.ACTION_BOOT_COMPLETED -> {
                            scheduleWork(context)
                            appWidgetIds.forEach { updateAppWidget(context, appWidgetManager, it, UpdateMode.FULL) }
                        }
                        ACTION_BATTERY_UPDATE -> {
                            appWidgetIds.forEach { updateAppWidget(context, appWidgetManager, it, UpdateMode.TICK) }
                        }
                        StepCounterService.ACTION_STEP_UPDATE -> {
                            // Step event updates match Tick mode conceptually 
                            appWidgetIds.forEach { updateAppWidget(context, appWidgetManager, it, UpdateMode.TICK) }
                        }
                        android.app.AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED -> {
                            appWidgetIds.forEach { updateAppWidget(context, appWidgetManager, it, UpdateMode.ALARM_ONLY) }
                        }
                        Intent.ACTION_PROVIDER_CHANGED -> {
                            val host = intent.data?.host
                            val mode = if (host == "com.android.calendar") UpdateMode.CALENDAR_ONLY else UpdateMode.TASKS_ONLY
                            appWidgetIds.forEach { updateAppWidget(context, appWidgetManager, it, mode) }
                        }
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun scheduleWork(context: Context) {
        val workRequest = androidx.work.PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
            15, java.util.concurrent.TimeUnit.MINUTES,
            5, java.util.concurrent.TimeUnit.MINUTES
        )
            .addTag("widget_update_work")
            .build()
        androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "WidgetUpdateTicker",
            androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    private fun cancelWork(context: Context) {
        androidx.work.WorkManager.getInstance(context).cancelUniqueWork("WidgetUpdateTicker")
    }

    companion object {
        const val ACTION_BATTERY_UPDATE = "com.leanbitlab.lwidget.ACTION_BATTERY_UPDATE"

        // Suspended function called from Coroutine
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, mode: UpdateMode = UpdateMode.FULL) {
            val prefs = context.getSharedPreferences("com.leanbitlab.lwidget.PREFS", Context.MODE_PRIVATE)

            // --- Load Preferences ---
            val showTime = prefs.getBoolean("show_time", true)
            val sizeTime = prefs.getFloat("size_time", 64f)
            
            val showDate = prefs.getBoolean("show_date", true)
            val sizeDate = prefs.getFloat("size_date", 14f)
            
            val showBattery = prefs.getBoolean("show_battery", true)
            val sizeBattery = prefs.getFloat("size_battery", 24f)
            
            val showTemp = prefs.getBoolean("show_temp", true)
            val sizeTemp = prefs.getFloat("size_temp", 18f)
            
            var showEvents = prefs.getBoolean("show_events", true)
            if (showEvents && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                showEvents = false
            }
            val sizeEvents = prefs.getFloat("size_events", 14f)

            val useSystemTheme = prefs.getBoolean("use_system_theme", false)
            val useDynamicColors = prefs.getBoolean("use_dynamic_colors", true)
            
            // Determine if light theme based on system or manual override
            val useLightTheme = if (useSystemTheme) {
                val nightMode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                nightMode != android.content.res.Configuration.UI_MODE_NIGHT_YES
            } else {
                false // Default to dark when system theme is off
            }
            
            val timeFormatIdx = prefs.getInt("time_format_idx", 0)
            val dateFormatIdx = prefs.getInt("date_format_idx", 0)
            
            var showData = prefs.getBoolean("show_data_usage", false)
            if (showData) {
                val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
                val opMode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    appOps.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
                } else {
                    @Suppress("DEPRECATION")
                    appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
                }
                if (opMode != android.app.AppOpsManager.MODE_ALLOWED) showData = false
            }
            val sizeData = prefs.getFloat("size_data", 14f)
            
            val showWorldClock = prefs.getBoolean("show_world_clock", false)
            val sizeWorldClock = prefs.getFloat("size_world_clock", 18f)
            val worldClockZoneStr = prefs.getString("world_clock_zone_str", "UTC") ?: "UTC"

            val showStorage = prefs.getBoolean("show_storage", true)
            val sizeStorage = prefs.getFloat("size_storage", 14f)

            var showTasks = prefs.getBoolean("show_tasks", false)
            if (showTasks && androidx.core.content.ContextCompat.checkSelfPermission(context, "org.tasks.permission.READ_TASKS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                showTasks = false
            }
            val sizeTasks = prefs.getFloat("size_tasks", 14f)

            val showNextAlarm = prefs.getBoolean("show_next_alarm", true)
            val sizeNextAlarm = prefs.getFloat("size_next_alarm", 14f)

            var showSteps = prefs.getBoolean("show_steps", false)
            if (showSteps && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACTIVITY_RECOGNITION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    showSteps = false
                }
            }
            val sizeSteps = prefs.getFloat("size_steps", 14f)

            // Make sure the background Step Service is running if steps are enabled
            val serviceIntent = Intent(context, StepCounterService::class.java)
            if (showSteps) {
                context.startForegroundService(serviceIntent)
            } else {
                context.stopService(serviceIntent)
            }


            val fontStyle = prefs.getInt("font_style", 0)
            
            val bgOpacity = prefs.getFloat("bg_opacity", 100f)
            val textColorPrimaryIdx = prefs.getInt("text_color_primary_idx", 0)
            val textColorSecondaryIdx = prefs.getInt("text_color_secondary_idx", 0)

            // --- Theme & Font Setup ---
            fun getLayout(fontIdx: Int): Int {
                 return when (fontIdx) {
                     1 -> R.layout.widget_layout_serif
                     2 -> R.layout.widget_layout_mono
                     3 -> R.layout.widget_layout_cursive
                     4 -> R.layout.widget_layout_condensed
                     5 -> R.layout.widget_layout_condensed_light
                     6 -> R.layout.widget_layout_light
                     7 -> R.layout.widget_layout_medium
                     8 -> R.layout.widget_layout_black
                     9 -> R.layout.widget_layout_thin
                     10 -> R.layout.widget_layout_smallcaps
                     else -> R.layout.widget_layout
                 }
            }

            val layoutId = getLayout(fontStyle)

            val views = RemoteViews(context.packageName, layoutId)

            // --- Background & Outline Application ---
            val outlineColorIdx = prefs.getInt("outline_color_idx", 0)
             
            // Background
            views.setImageViewResource(R.id.widget_background, R.drawable.widget_bg_fill)
            // Using ColorFilter to tint the white shape
            views.setInt(R.id.widget_background, "setColorFilter", if (useLightTheme) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#212121")) 
            
            val alpha255 = (bgOpacity * 255 / 100).toInt().coerceIn(0, 255)
            views.setInt(R.id.widget_background, "setImageAlpha", alpha255)

            // Outline
            // Resolve outline using same logic (0=Default, 1=System, 2=Custom)
            fun resolveOutlineColor(idx: Int): Int {
                 return when (idx) {
                     0 -> context.getColor(R.color.widget_outline) // Default
                     1 -> if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                              context.getColor(android.R.color.system_accent1_500)
                          } else {
                              android.graphics.Color.CYAN
                          }
                     2 -> {
                          val r = prefs.getInt("outline_color_r", 255)
                          val g = prefs.getInt("outline_color_g", 255)
                          val b = prefs.getInt("outline_color_b", 255)
                          android.graphics.Color.rgb(r, g, b)
                     }
                     else -> context.getColor(R.color.widget_outline)
                 }
            }
            
            val showOutline = prefs.getBoolean("show_outline", true)
            val outlineColor = resolveOutlineColor(outlineColorIdx)
            views.setImageViewResource(R.id.widget_outline, R.drawable.widget_bg_outline)
            views.setViewVisibility(R.id.widget_outline, if (showOutline) android.view.View.VISIBLE else android.view.View.GONE)
            views.setInt(R.id.widget_outline, "setColorFilter", outlineColor)
            views.setInt(R.id.widget_outline, "setImageAlpha", 255)

            // Resolve Colors
            fun resolveColor(idx: Int, isPrimary: Boolean, isLight: Boolean): Int {
                 // When dynamic colors is on, always use dynamic palette regardless of saved color index
                 if (useDynamicColors && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                     return if (isPrimary) {
                         // High contrast accent for time & battery
                         context.getColor(if (isLight) android.R.color.system_accent1_800 else android.R.color.system_accent1_50)
                     } else {
                         // Muted neutral for secondary items (temp, data, storage, steps)
                         context.getColor(if (isLight) android.R.color.system_neutral2_600 else android.R.color.system_neutral2_300)
                     }
                 }
                 return when (idx) {
                     0 -> { // Default
                         if (isPrimary) {
                             if (isLight) context.getColor(R.color.widget_text_light) else android.graphics.Color.WHITE
                         } else {
                             if (isLight) context.getColor(R.color.widget_text_secondary_light) else android.graphics.Color.parseColor("#CCFFFFFF")
                         }
                     }
                     1 -> if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                              context.getColor(android.R.color.system_accent1_500)
                          } else {
                              android.graphics.Color.CYAN
                          }
                     2 -> {
                         val prefix = if (isPrimary) "text_color_primary" else "text_color_secondary"
                         val r = prefs.getInt("${prefix}_r", 255)
                         val g = prefs.getInt("${prefix}_g", 255)
                         val b = prefs.getInt("${prefix}_b", 255)
                         android.graphics.Color.rgb(r, g, b)
                     }
                     else -> if (isPrimary) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#CCFFFFFF")
                 }
            }
            
            val primaryColor = resolveColor(textColorPrimaryIdx, true, useLightTheme)
            val secondaryColor = resolveColor(textColorSecondaryIdx, false, useLightTheme)

            // Slightly distinct colors for date and next alarm
            val dateColor = if (useDynamicColors && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                // Warm accent for date
                context.getColor(if (useLightTheme) android.R.color.system_accent2_700 else android.R.color.system_accent2_100)
            } else {
                if (useLightTheme) android.graphics.Color.parseColor("#AA555544") else android.graphics.Color.parseColor("#BBDDDDCC")
            }
            val alarmColor = if (useDynamicColors && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                // Cool tertiary accent for alarm
                context.getColor(if (useLightTheme) android.R.color.system_accent3_700 else android.R.color.system_accent3_100)
            } else {
                if (useLightTheme) android.graphics.Color.parseColor("#AA445566") else android.graphics.Color.parseColor("#BBAACCDD")
            }

            // Background & outline dynamic color
            if (useDynamicColors && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                // Warm neutral surface for background
                views.setInt(R.id.widget_background, "setColorFilter", context.getColor(if (useLightTheme) android.R.color.system_neutral2_50 else android.R.color.system_neutral1_800))
                // Accent-tinted outline
                if (showOutline) {
                    views.setInt(R.id.widget_outline, "setColorFilter", context.getColor(if (useLightTheme) android.R.color.system_accent1_300 else android.R.color.system_accent1_400))
                }
            } else {
                views.setInt(R.id.widget_background, "setColorFilter", if (useLightTheme) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#212121")) 
            }

            if (mode == UpdateMode.TICK) {
                val tickViews = RemoteViews(context.packageName, layoutId)
                val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
                    context.registerReceiver(null, ifilter)
                }
                val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 0
                val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
                val batteryPct = (level * 100 / scale.toFloat()).toInt()
                val tempInt = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
                val tempVal = tempInt / 10f
                if (showSteps) loadStepCount(context, tickViews)
                if (showBattery) tickViews.setTextViewText(R.id.text_battery, "$batteryPct%")
                if (showTemp) tickViews.setTextViewText(R.id.text_temp, "${String.format("%.1f", tempVal)}°C")
                if (showData) updateDataUsage(context, tickViews)
                if (showStorage) updateStorageStats(tickViews)
                appWidgetManager.partiallyUpdateAppWidget(appWidgetId, tickViews)
                return
            } else if (mode == UpdateMode.CALENDAR_ONLY) {
                val calViews = RemoteViews(context.packageName, layoutId)
                if (showEvents) loadCalendarEvents(context, calViews, sizeEvents, primaryColor, secondaryColor)
                appWidgetManager.partiallyUpdateAppWidget(appWidgetId, calViews)
                return
            } else if (mode == UpdateMode.TASKS_ONLY) {
                val taskViews = RemoteViews(context.packageName, layoutId)
                if (showTasks) loadTasks(context, taskViews, sizeTasks, primaryColor)
                appWidgetManager.partiallyUpdateAppWidget(appWidgetId, taskViews)
                return
            } else if (mode == UpdateMode.ALARM_ONLY) {
                val alarmViews = RemoteViews(context.packageName, layoutId)
                if (showNextAlarm) loadNextAlarm(context, alarmViews, sizeNextAlarm, secondaryColor)
                appWidgetManager.partiallyUpdateAppWidget(appWidgetId, alarmViews)
                return
            }

            // --- Apply Time ---
            val timeVisible = showTime || showWorldClock
            views.setViewVisibility(R.id.time_container, if (timeVisible) android.view.View.VISIBLE else android.view.View.GONE)
            
            views.setViewVisibility(R.id.clock_time, if (showTime) android.view.View.VISIBLE else android.view.View.GONE)
            views.setTextViewTextSize(R.id.clock_time, android.util.TypedValue.COMPLEX_UNIT_SP, sizeTime)
            views.setTextColor(R.id.clock_time, primaryColor)
            
            val (timeFormat12, timeFormat24) = when(timeFormatIdx) {
                0 -> "h:mm" to "H:mm"
                1 -> "H:mm" to "H:mm"
                else -> "h:mm" to "H:mm"
            }
            views.setCharSequence(R.id.clock_time, "setFormat12Hour", timeFormat12)
            views.setCharSequence(R.id.clock_time, "setFormat24Hour", timeFormat24)

            // --- World Clock ---
            views.setViewVisibility(R.id.text_world_clock, if (showWorldClock) android.view.View.VISIBLE else android.view.View.GONE)
            if (showWorldClock) {
                loadWorldClock(views, sizeWorldClock, secondaryColor, worldClockZoneStr, timeFormat12.contains("a"))
            }

            // --- Apply Date ---
            val dateVisible = showDate || showNextAlarm
            views.setViewVisibility(R.id.date_container, if (dateVisible) android.view.View.VISIBLE else android.view.View.GONE)

            views.setViewVisibility(R.id.clock_date, if (showDate) android.view.View.VISIBLE else android.view.View.GONE)
            views.setTextViewTextSize(R.id.clock_date, android.util.TypedValue.COMPLEX_UNIT_SP, sizeDate)
            views.setTextColor(R.id.clock_date, dateColor)
            
            val (dateFormat12, dateFormat24) = when(dateFormatIdx) {
                0 -> "EEEE, MMMM dd" to "EEEE, MMMM dd"
                1 -> "EEE, MMM dd" to "EEE, MMM dd"
                2 -> "dd/MM/yyyy" to "dd/MM/yyyy"
                else -> "EEEE, MMMM dd" to "EEEE, MMMM dd"
            }
            views.setCharSequence(R.id.clock_date, "setFormat12Hour", dateFormat12)
            views.setCharSequence(R.id.clock_date, "setFormat24Hour", dateFormat24)

            // --- Apply Battery & Temp ---
            views.setViewVisibility(R.id.text_battery, if (showBattery) android.view.View.VISIBLE else android.view.View.GONE)
            views.setTextViewTextSize(R.id.text_battery, android.util.TypedValue.COMPLEX_UNIT_SP, sizeBattery)
            views.setTextColor(R.id.text_battery, primaryColor)
            
            views.setViewVisibility(R.id.text_temp, if (showTemp) android.view.View.VISIBLE else android.view.View.GONE)
            views.setTextViewTextSize(R.id.text_temp, android.util.TypedValue.COMPLEX_UNIT_SP, sizeTemp)
            views.setTextColor(R.id.text_temp, secondaryColor)

            val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
                context.registerReceiver(null, ifilter)
            }
            
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 0
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
            val batteryPct = (level * 100 / scale.toFloat()).toInt()
            
            val tempInt = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            val tempVal = tempInt / 10f

            if (showBattery) {
                views.setTextViewText(R.id.text_battery, "$batteryPct%")
            }
            if (showTemp) {
                views.setTextViewText(R.id.text_temp, "${String.format("%.1f", tempVal)}°C")
            }
            
            // --- Data Usage ---
            views.setViewVisibility(R.id.text_data_usage, if (showData) android.view.View.VISIBLE else android.view.View.GONE)
            if (showData) {
                views.setTextViewTextSize(R.id.text_data_usage, android.util.TypedValue.COMPLEX_UNIT_SP, sizeData)
                views.setTextColor(R.id.text_data_usage, secondaryColor)
                updateDataUsage(context, views)
            }

            // --- Storage ---
            views.setViewVisibility(R.id.text_storage, if (showStorage) android.view.View.VISIBLE else android.view.View.GONE)
            if (showStorage) {
                views.setTextViewTextSize(R.id.text_storage, android.util.TypedValue.COMPLEX_UNIT_SP, sizeStorage)
                views.setTextColor(R.id.text_storage, secondaryColor)
                updateStorageStats(views)
            }

            // --- Step Counter ---
            views.setViewVisibility(R.id.text_steps, if (showSteps) android.view.View.VISIBLE else android.view.View.GONE)
            if (showSteps) {
                views.setTextViewTextSize(R.id.text_steps, android.util.TypedValue.COMPLEX_UNIT_SP, sizeSteps)
                views.setTextColor(R.id.text_steps, secondaryColor)
                loadStepCount(context, views)
            }
            
            // --- Dynamic Spacing Logic for Both Sides ---
            fun dpToPx(dp: Float): Int {
                return (dp * context.resources.displayMetrics.density).toInt()
            }

            // To precisely manage font intrinsic top padding, we drop the container's top padding to 0
            // and apply a custom top padding precisely computed based on the top item sizes.
            val basePadding = dpToPx(16f)
            views.setViewPadding(R.id.inner_container, basePadding, 0, basePadding, basePadding)

            // Left Side: Time or Date or Events
            if (showTime || showWorldClock) {
                val size = if (showTime) sizeTime else sizeWorldClock
                val intrinsicGap = size * 0.18f
                views.setViewPadding(R.id.time_container, 0, maxOf(0, dpToPx(16f - intrinsicGap)), 0, 0)
                views.setViewPadding(R.id.date_container, 0, 0, 0, 0)
            } else if (showDate || showNextAlarm) {
                views.setViewPadding(R.id.time_container, 0, 0, 0, 0)
                val size = if (showDate) sizeDate else sizeNextAlarm
                val intrinsicGap = size * 0.18f
                views.setViewPadding(R.id.date_container, 0, maxOf(0, dpToPx(16f - intrinsicGap)), 0, 0)
            } else {
                views.setViewPadding(R.id.time_container, 0, 0, 0, 0)
                views.setViewPadding(R.id.date_container, 0, 0, 0, 0)
            }
            
            // Events container: add spacing gap below date/time if they exist
            val eventsVisible = showEvents || showTasks
            val leftHasContent = (showTime || showWorldClock || showDate || showNextAlarm)
            if (eventsVisible) {
                val topMargin = if (leftHasContent) dpToPx(8f) else {
                    val size = if (showEvents) sizeEvents else sizeTasks
                    val intrinsicGap = size * 0.18f
                    maxOf(0, dpToPx(16f - intrinsicGap))
                }
                views.setViewPadding(R.id.events_container, 0, topMargin, 0, 0)
            }

            // Right Side Stack: Battery -> Temp -> Data -> Storage
            data class StackEntry(val viewId: Int, val isVisible: Boolean, val size: Float)

            val rightStack = listOf(
                StackEntry(R.id.text_battery, showBattery, sizeBattery),
                StackEntry(R.id.text_temp, showTemp, sizeTemp),
                StackEntry(R.id.text_data_usage, showData, sizeData),
                StackEntry(R.id.text_storage, showStorage, sizeStorage),
                StackEntry(R.id.text_steps, showSteps, sizeSteps)
            )

            var rightFirstVisible = true
            for (entry in rightStack) {
                if (entry.isVisible) {
                    val topPaddingPx = if (rightFirstVisible) {
                        val intrinsicGap = entry.size * 0.18f
                        maxOf(0, dpToPx(16f - intrinsicGap))
                    } else {
                        val stackMargin = when(entry.viewId) {
                            R.id.text_data_usage, R.id.text_storage -> 2f
                            else -> 0f
                        }
                        dpToPx(stackMargin)
                    }
                    views.setViewPadding(entry.viewId, 0, topPaddingPx, 0, 0)
                    rightFirstVisible = false
                } else {
                    views.setViewPadding(entry.viewId, 0, 0, 0, 0)
                }
            }

            // --- Click Actions ---
            val clockPackages = listOf("com.android.deskclock", "com.google.android.deskclock", "com.simplemobiletools.clock", "org.fossify.clock")
            val alarmIntent = getBestIntent(context, clockPackages, Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS))
            val alarmPendingIntent = PendingIntent.getActivity(context, 0, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.clock_time, alarmPendingIntent)

            val calendarPackages = listOf("org.fossify.calendar", "com.simplemobiletools.calendar", "com.google.android.calendar", "com.android.calendar")
            val baseCalIntent = Intent(Intent.ACTION_VIEW).apply { 
                data = android.net.Uri.parse("content://com.android.calendar/time") 
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val calendarIntent = getBestIntent(context, calendarPackages, baseCalIntent)
            val calendarPendingIntent = PendingIntent.getActivity(context, 1, calendarIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.clock_date, calendarPendingIntent)

            val batteryIntent = Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
            val batteryPendingIntent = PendingIntent.getActivity(context, 2, batteryIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.text_battery, batteryPendingIntent)
            views.setOnClickPendingIntent(R.id.text_temp, batteryPendingIntent)
            
            val storageIntent = Intent(android.provider.Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
            val storagePendingIntent = PendingIntent.getActivity(context, 3, storageIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.text_storage, storagePendingIntent)

            // --- Calendar Events OR Tasks ---
            views.setViewVisibility(R.id.events_container, if (showEvents || showTasks) android.view.View.VISIBLE else android.view.View.GONE)
            
            if (showEvents) {
                loadCalendarEvents(context, views, sizeEvents, primaryColor, secondaryColor)
            } else if (showTasks) {
                loadTasks(context, views, sizeTasks, primaryColor)
            }

            // --- Next Alarm ---
            views.setViewVisibility(R.id.text_next_alarm, if (showNextAlarm) android.view.View.VISIBLE else android.view.View.GONE)
            if (showNextAlarm) {
                loadNextAlarm(context, views, sizeNextAlarm, alarmColor)
            }
            // Click action for Next Alarm (same as Clock)
            views.setOnClickPendingIntent(R.id.text_next_alarm, alarmPendingIntent)

            val refreshIntent = Intent(context, AwidgetProvider::class.java).apply {
                action = ACTION_BATTERY_UPDATE
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(context, 10, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            if (showTasks) {
                 val tasksIntent = context.packageManager.getLaunchIntentForPackage("org.tasks")
                 if (tasksIntent != null) {
                     val tasksPendingIntent = PendingIntent.getActivity(context, 11, tasksIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                     views.setOnClickPendingIntent(R.id.events_container, tasksPendingIntent)
                 } else {
                     views.setOnClickPendingIntent(R.id.events_container, refreshPendingIntent)
                 }
            } else {
                 views.setOnClickPendingIntent(R.id.events_container, refreshPendingIntent)
            }

            val settingsIntent = Intent(context, MainActivity::class.java)
            val settingsPendingIntent = PendingIntent.getActivity(context, 0, settingsIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_root, settingsPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }


        private fun loadCalendarEvents(context: Context, views: RemoteViews, textSizeSp: Float, primaryColor: Int, secondaryColor: Int) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.READ_CALENDAR
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return
            }

            val eventViews = listOf(
                R.id.text_event_1, R.id.text_event_2, R.id.text_event_3,
                R.id.text_event_4, R.id.text_event_5, R.id.text_event_6,
                R.id.text_event_7, R.id.text_event_8, R.id.text_event_9,
                R.id.text_event_10
            )

            try {
                val syncedCalendarIds = mutableSetOf<Long>()
                val visibleCalendarIds = mutableSetOf<Long>()
                
                val calSelection = "${android.provider.CalendarContract.Calendars.VISIBLE} = 1"

            context.contentResolver.query(
                android.provider.CalendarContract.Calendars.CONTENT_URI,
                arrayOf(
                    android.provider.CalendarContract.Calendars._ID, 
                    android.provider.CalendarContract.Calendars.ACCOUNT_TYPE,
                    android.provider.CalendarContract.Calendars.ACCOUNT_NAME,
                    android.provider.CalendarContract.Calendars.CALENDAR_DISPLAY_NAME
                ),
                calSelection, null, null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(android.provider.CalendarContract.Calendars._ID)
                // val typeIdx = cursor.getColumnIndex(android.provider.CalendarContract.Calendars.ACCOUNT_TYPE)
                val nameIdx = cursor.getColumnIndex(android.provider.CalendarContract.Calendars.ACCOUNT_NAME)
                val displayIdx = cursor.getColumnIndex(android.provider.CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val calId = cursor.getLong(idIdx)
                    // val accountType = cursor.getString(typeIdx) ?: ""
                    val accountName = cursor.getString(nameIdx) ?: ""
                    val displayName = cursor.getString(displayIdx) ?: ""
                    
                    visibleCalendarIds.add(calId)

                    if (displayName.contains("holiday", ignoreCase = true) ||
                        accountName.contains("holiday", ignoreCase = true)) {
                        syncedCalendarIds.add(calId)
                    }
                }
            }
            
            if (visibleCalendarIds.isEmpty()) return

            val projection = arrayOf(
                android.provider.CalendarContract.Instances.EVENT_ID,
                android.provider.CalendarContract.Events.TITLE,
                android.provider.CalendarContract.Instances.BEGIN,
                android.provider.CalendarContract.Instances.CALENDAR_ID
            )

            val now = System.currentTimeMillis()
            val endQuery = now + android.text.format.DateUtils.DAY_IN_MILLIS * 30 

            val uri = android.provider.CalendarContract.Instances.CONTENT_URI.buildUpon()
                .appendPath(now.toString())
                .appendPath(endQuery.toString())
                .build()

                val idList = visibleCalendarIds.joinToString(",")
                // Removed Instances.VISIBLE = 1 because some devices/ROMs crash if Instances table lacks this column.
                // We are already filtering by CALENDAR_ID IN ($idList) which only contains visibly enabled calendars.
                val selection = "${android.provider.CalendarContract.Instances.END} >= ? AND ${android.provider.CalendarContract.Instances.CALENDAR_ID} IN ($idList)"
                val selectionArgs = arrayOf(now.toString())
            val sortOrder = "${android.provider.CalendarContract.Instances.BEGIN} ASC"

            data class EventInfo(val id: Long, val title: String, val begin: Long, val isLocal: Boolean)
            val events = mutableListOf<EventInfo>()

            context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                val eventIdIdx = cursor.getColumnIndex(android.provider.CalendarContract.Instances.EVENT_ID)
                val titleIdx = cursor.getColumnIndex(android.provider.CalendarContract.Events.TITLE)
                val beginIdx = cursor.getColumnIndex(android.provider.CalendarContract.Instances.BEGIN)
                val calIdIdx = cursor.getColumnIndex(android.provider.CalendarContract.Instances.CALENDAR_ID)

                while (cursor.moveToNext() && events.size < 10) {
                    val eventId = cursor.getLong(eventIdIdx)
                    val title = cursor.getString(titleIdx) ?: "No Title"
                    val begin = cursor.getLong(beginIdx)
                    val calId = cursor.getLong(calIdIdx)
                    val isLocal = !syncedCalendarIds.contains(calId)
                    events.add(EventInfo(eventId, title, begin, isLocal))
                }
            }

            // java.time formatters
            val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
            val dayFormatter = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
            val dateFormatter = DateTimeFormatter.ofPattern("d MMM h:mma", Locale.getDefault())

            for (i in eventViews.indices) {
                if (i < events.size) {
                    val event = events[i]
                    val eventTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.begin), ZoneId.systemDefault())
                    val today = LocalDate.now()
                    val oneWeekLater = today.plusWeeks(1)
                    
                    val timeText = if (eventTime.toLocalDate().isEqual(today)) {
                        eventTime.format(timeFormatter)
                    } else if (eventTime.toLocalDate().isBefore(oneWeekLater)) {
                        "${eventTime.format(dayFormatter)} ${eventTime.format(timeFormatter)}"
                    } else {
                        eventTime.format(dateFormatter)
                    }
                    
                    val fullText = "• $timeText  ${event.title}"
                    val spannable = SpannableString(fullText)
                    val accentColor = context.getColor(R.color.widget_outline) 
                    spannable.setSpan(ForegroundColorSpan(accentColor), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    
                    views.setTextViewText(eventViews[i], spannable)
                    views.setTextColor(eventViews[i], if (event.isLocal) primaryColor else secondaryColor)
                    views.setTextViewTextSize(eventViews[i], android.util.TypedValue.COMPLEX_UNIT_SP, textSizeSp)
                    views.setViewVisibility(eventViews[i], android.view.View.VISIBLE)
                    
                    val eventIntent = Intent(Intent.ACTION_VIEW).apply {
                        data = android.content.ContentUris.withAppendedId(android.provider.CalendarContract.Events.CONTENT_URI, event.id)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                        val eventPendingIntent = PendingIntent.getActivity(context, event.id.toInt(), eventIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                        views.setOnClickPendingIntent(eventViews[i], eventPendingIntent)
                    } else {
                        views.setViewVisibility(eventViews[i], android.view.View.GONE)
                    }
                }
            } catch (e: Exception) {
                // Log and gracefully handle crash
                android.util.Log.e("LWidget", "Error loading calendar events", e)
                for (viewId in eventViews) {
                    views.setViewVisibility(viewId, android.view.View.GONE)
                }
            }
        }

        private fun updateDataUsage(context: Context, views: RemoteViews) {
            val networkStatsManager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
            // Use java.time
            val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endTime = System.currentTimeMillis()

            try {
                val bucket = networkStatsManager.querySummaryForDevice(
                    NetworkCapabilities.TRANSPORT_CELLULAR,
                    null,
                    startOfDay,
                    endTime
                )
                
                val bytes = bucket.rxBytes + bucket.txBytes
                val mb = bytes / (1024f * 1024f)
                
                val text = if (mb >= 1000) {
                     String.format("%.2f GB 📶", mb / 1024f)
                } else {
                     String.format("%.1f MB 📶", mb)
                }
                
                views.setTextViewText(R.id.text_data_usage, text)
                
            } catch (e: SecurityException) {
                val res = context.resources
                // Assuming R.string.no_perm exists (we created strings.xml)
                views.setTextViewText(R.id.text_data_usage, res.getString(R.string.no_perm))
            } catch (e: Exception) {
                val res = context.resources
                views.setTextViewText(R.id.text_data_usage, res.getString(R.string.error))
            }
        }

        private fun loadTasks(context: Context, views: RemoteViews, textSizeSp: Float, primaryColor: Int) {
            val eventViews = listOf(
                R.id.text_event_1, R.id.text_event_2, R.id.text_event_3,
                R.id.text_event_4, R.id.text_event_5, R.id.text_event_6,
                R.id.text_event_7, R.id.text_event_8, R.id.text_event_9,
                R.id.text_event_10
            )
            
            // Debugging: Check permission again contextually
            val hasPerm = context.checkSelfPermission("org.tasks.permission.READ_TASKS") == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                          context.checkSelfPermission("com.todoroo.astrid.READ") == android.content.pm.PackageManager.PERMISSION_GRANTED
            
            if (!hasPerm) {
                 views.setTextViewText(eventViews[0], "Missing Permission")
                 views.setViewVisibility(eventViews[0], android.view.View.VISIBLE)
                 return
            }


            val taskUri = android.net.Uri.parse("content://org.tasks/tasks")
            // Selection appears to be ignored by provider, so we select all and filter manually
            val selection = null
            
            try {
                context.contentResolver.query(taskUri, null, selection, null, "due ASC")?.use { cursor ->
                     val titleIdx = cursor.getColumnIndex("title")
                     
                     if (cursor.count == 0) {
                         // views.setTextViewText(eventViews[0], "No active tasks found")
                         // views.setViewVisibility(eventViews[0], android.view.View.VISIBLE)
                         // views.setTextColor(eventViews[0], secondaryColor)
                         // Just hide all
                         for (viewId in eventViews) {
                             views.setViewVisibility(viewId, android.view.View.GONE)
                         }
                         return
                     }

                     var i = 0
                     while (cursor.moveToNext() && i < eventViews.size) {
                         // Manual Filtering: Provider might ignore selection
                         val compIdx = cursor.getColumnIndex("completed")
                         val delIdx = cursor.getColumnIndex("deleted")
                         val completed = if (compIdx >= 0) cursor.getString(compIdx) else null
                         val deleted = if (delIdx >= 0) cursor.getString(delIdx) else null
                         
                         val isCompleted = completed != null && completed != "0"
                         val isDeleted = deleted != null && deleted != "0"
                         
                         if (isCompleted || isDeleted) {
                             continue
                         }

                         if (titleIdx != -1) {
                             val title = cursor.getString(titleIdx) ?: "No Title"
                             
                             val fullText = "• $title"
                             val spannable = SpannableString(fullText)
                             val accentColor = context.getColor(R.color.widget_outline) 
                             spannable.setSpan(ForegroundColorSpan(accentColor), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                             
                             views.setTextViewText(eventViews[i], spannable)
                             views.setTextColor(eventViews[i], primaryColor)
                             views.setTextViewTextSize(eventViews[i], android.util.TypedValue.COMPLEX_UNIT_SP, textSizeSp)
                             views.setViewVisibility(eventViews[i], android.view.View.VISIBLE)
                             i++
                         }
                     }
                     
                     for (j in i until eventViews.size) {
                         views.setViewVisibility(eventViews[j], android.view.View.GONE)
                     }
                     return
                }
            } catch (e: Exception) {
                // Fail silently or log
                for (viewId in eventViews) {
                     views.setViewVisibility(viewId, android.view.View.GONE)
                }
            }
            
            // If we reached here (query null?), show generic message
            // views.setTextViewText(eventViews[0], "Query Failed")
            // views.setViewVisibility(eventViews[0], android.view.View.VISIBLE)
        }

        private fun loadWorldClock(views: RemoteViews, textSizeSp: Float, textColor: Int, zoneIdStr: String, is12Hour: Boolean) {
             try {
                 val zoneId = ZoneId.of(zoneIdStr)
                 val zdt = java.time.ZonedDateTime.now(zoneId)
                 val pattern = if (is12Hour) "h:mm a" else "H:mm"
                 val formatter = DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
                 val timeStr = zdt.format(formatter)
                 
                 // If label is too long, maybe truncate? For now, let it be.
                 // Format: "10:30 AM" (Time only, subtle)
                 views.setTextViewText(R.id.text_world_clock, timeStr)
                 views.setTextViewTextSize(R.id.text_world_clock, android.util.TypedValue.COMPLEX_UNIT_SP, textSizeSp)
                 views.setTextColor(R.id.text_world_clock, textColor)
                 views.setViewVisibility(R.id.text_world_clock, android.view.View.VISIBLE)

             } catch (e: Exception) {
                 views.setViewVisibility(R.id.text_world_clock, android.view.View.GONE)
             }
        }

        private fun loadNextAlarm(context: Context, views: RemoteViews, textSizeSp: Float, textColor: Int) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val nextAlarm = alarmManager.nextAlarmClock
            
            if (nextAlarm != null) {
                val nextAlarmTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(nextAlarm.triggerTime), ZoneId.systemDefault())
                val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
                val timeText = nextAlarmTime.format(timeFormatter)
                
                // Format: "| ⏰ 7:00 AM"
                val fullText = "| ⏰ $timeText"
                views.setTextViewText(R.id.text_next_alarm, fullText)
                views.setTextViewTextSize(R.id.text_next_alarm, android.util.TypedValue.COMPLEX_UNIT_SP, textSizeSp)
                views.setTextColor(R.id.text_next_alarm, textColor)
                views.setViewVisibility(R.id.text_next_alarm, android.view.View.VISIBLE)
            } else {
                 views.setViewVisibility(R.id.text_next_alarm, android.view.View.GONE)
            }
        }

        private fun updateStorageStats(views: RemoteViews) {
             try {
                 val path = android.os.Environment.getDataDirectory()
                 val stat = android.os.StatFs(path.path)
                 val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
                 
                 val gb = freeBytes / (1024f * 1024f * 1024f)
                 
                 // Concisely: "12 GB"
                 val text = String.format("%.0f GB 💾", gb)
                 
                 views.setTextViewText(R.id.text_storage, text)
             } catch (e: Exception) {
                 views.setTextViewText(R.id.text_storage, "Err")
             }
        }

        private fun loadStepCount(context: Context, views: RemoteViews) {
            try {
                val prefs = context.getSharedPreferences("com.leanbitlab.lwidget.PREFS", Context.MODE_PRIVATE)
                val totalSteps = prefs.getFloat("last_total_steps", 0f)
                val baselineSteps = prefs.getFloat("step_baseline", 0f)

                val dailySteps = (totalSteps - baselineSteps).toInt().coerceAtLeast(0)
                views.setTextViewText(R.id.text_steps, "$dailySteps \uD83D\uDEB6") // 🚶
            } catch (e: Exception) {
                // Fallback display if an exception occurs
                views.setTextViewText(R.id.text_steps, "Err")
            }
        }

        private fun getBestIntent(context: Context, packages: List<String>, fallback: Intent): Intent {
            val pm = context.packageManager
            for (pkg in packages) {
                try {
                    val intent = pm.getLaunchIntentForPackage(pkg)
                    if (intent != null) {
                        return intent
                    }
                } catch (e: Exception) {
                }
            }
            return fallback
        }
    }
}
