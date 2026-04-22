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
                updateAppWidget(context, appWidgetManager, appWidgetIds, UpdateMode.FULL)
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
            android.app.AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED,
            "nodomain.freeyourgadget.gadgetbridge.ACTION_GENERIC_WEATHER"
        )) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    when (intent.action) {
                        Intent.ACTION_BOOT_COMPLETED -> {
                            scheduleWork(context)
                            if (appWidgetIds.isNotEmpty()) updateAppWidget(context, appWidgetManager, appWidgetIds, UpdateMode.FULL)
                        }
                        ACTION_BATTERY_UPDATE -> {
                            if (appWidgetIds.isNotEmpty()) updateAppWidget(context, appWidgetManager, appWidgetIds, UpdateMode.TICK)
                        }
                        StepCounterService.ACTION_STEP_UPDATE -> {
                            // Step event updates match Tick mode conceptually 
                            if (appWidgetIds.isNotEmpty()) updateAppWidget(context, appWidgetManager, appWidgetIds, UpdateMode.TICK)
                        }
                        android.app.AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED -> {
                            if (appWidgetIds.isNotEmpty()) updateAppWidget(context, appWidgetManager, appWidgetIds, UpdateMode.ALARM_ONLY)
                        }
                        Intent.ACTION_PROVIDER_CHANGED -> {
                            val host = intent.data?.host
                            val mode = if (host == "com.android.calendar") UpdateMode.CALENDAR_ONLY else UpdateMode.TASKS_ONLY
                            if (appWidgetIds.isNotEmpty()) updateAppWidget(context, appWidgetManager, appWidgetIds, mode)
                        }
                        "nodomain.freeyourgadget.gadgetbridge.ACTION_GENERIC_WEATHER" -> {
                            val weatherJson = intent.getStringExtra("WeatherJson")
                            if (!weatherJson.isNullOrEmpty()) {
                                com.leanbitlab.lwidget.weather.BreezyWeatherFetcher.saveLatestWeatherData(context, weatherJson)
                                if (appWidgetIds.isNotEmpty()) updateAppWidget(context, appWidgetManager, appWidgetIds, UpdateMode.FULL)
                            }
                        }
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun scheduleWork(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = Intent(context, AwidgetProvider::class.java).apply {
            action = ACTION_BATTERY_UPDATE
        }
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            500,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        
        // 1 minute
        val intervalMillis = 1L * 60L * 1000L
        
        alarmManager.setInexactRepeating(
            android.app.AlarmManager.RTC,
            System.currentTimeMillis() + intervalMillis,
            intervalMillis,
            pendingIntent
        )
    }

    private fun cancelWork(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = Intent(context, AwidgetProvider::class.java).apply {
            action = ACTION_BATTERY_UPDATE
        }
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            500,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    companion object {
        const val ACTION_BATTERY_UPDATE = "com.leanbitlab.lwidget.ACTION_BATTERY_UPDATE"
        const val PERMISSION_READ_TASKS_ORG = "org.tasks.permission.READ_TASKS"
        const val PERMISSION_READ_TASKS_ASTRID = "com.todoroo.astrid.READ"

        // Suspended function called from Coroutine
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray, mode: UpdateMode = UpdateMode.FULL) {
            val prefs = context.getSharedPreferences("com.leanbitlab.lwidget.PREFS", Context.MODE_PRIVATE)

            // --- Load Preferences ---
            val showTime = prefs.getBoolean("show_time", true)
            val sizeTime = prefs.getFloat("size_time", 64f)
            
            val showDate = prefs.getBoolean("show_date", true)
            val sizeDate = prefs.getFloat("size_date", 14f)
            
            val showBattery = prefs.getBoolean("show_battery", true)
            val sizeBattery = prefs.getFloat("size_battery", 24f)
            val boldBattery = prefs.getBoolean("bold_battery", false)
            
            val showTemp = prefs.getBoolean("show_temp", true)
            val sizeTemp = prefs.getFloat("size_temp", 18f)
            val boldTemp = prefs.getBoolean("bold_temp", false)
            
            val showWeatherCondition = prefs.getBoolean("show_weather_condition", false)
            val sizeWeather = prefs.getFloat("size_weather", 18f)
            val boldWeather = prefs.getBoolean("bold_weather", false)
            
            var showEvents = prefs.getBoolean("show_events", true)
            if (showEvents && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                showEvents = false
            }
            val sizeEvents = prefs.getFloat("size_events", 14f)

            // Fetch Breezy Weather Data
            val bweather = com.leanbitlab.lwidget.weather.BreezyWeatherFetcher.fetchLocalWeather(context)
            val showWeatherIconOnly = prefs.getBoolean("show_weather_icon_only", false) 
            
            android.util.Log.d("WidgetLife", "UpdateMode FULL | Condition: $showWeatherCondition | IconOnly: $showWeatherIconOnly | WeatherData: ${bweather?.currentCondition}")

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
            if (showTasks && androidx.core.content.ContextCompat.checkSelfPermission(context, PERMISSION_READ_TASKS_ORG) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
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

            // Make sure the background Step Service is running if steps or keep-alive is enabled
            val keepAlive = prefs.getBoolean("keep_alive", false)
            val serviceIntent = Intent(context, StepCounterService::class.java)
            // FOREGROUND_SERVICE_TYPE_HEALTH requires ACTIVITY_RECOGNITION at runtime
            val hasActivityPerm = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACTIVITY_RECOGNITION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else true
            if ((showSteps || keepAlive) && hasActivityPerm) {
                context.startForegroundService(serviceIntent)
            } else {
                context.stopService(serviceIntent)
            }


            val fontStyle = prefs.getInt("font_style", 0)
            
            val bgOpacity = prefs.getFloat("bg_opacity", 100f)
            val textColorPrimaryIdx = prefs.getInt("text_color_primary_idx", 0)
            val textColorSecondaryIdx = prefs.getInt("text_color_secondary_idx", 0)
            val bgColorIdx = prefs.getInt("bg_color_idx", 0)

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

            // Resolve background color (0=Default, 1=System Accent, 2=Custom)
            fun resolveBgColor(idx: Int, isLight: Boolean): Int {
                 return when (idx) {
                     0 -> if (isLight) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#212121")
                     1 -> if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                              context.getColor(android.R.color.system_accent1_500)
                          } else {
                              android.graphics.Color.CYAN
                          }
                     2 -> {
                          val r = prefs.getInt("bg_color_r", 255)
                          val g = prefs.getInt("bg_color_g", 255)
                          val b = prefs.getInt("bg_color_b", 255)
                          android.graphics.Color.rgb(r, g, b)
                     }
                     else -> if (isLight) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#212121")
                 }
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                views.setColorStateList(R.id.widget_background, "setImageTintList", android.content.res.ColorStateList.valueOf(resolveBgColor(bgColorIdx, useLightTheme)))
            } else {
                views.setInt(R.id.widget_background, "setColorFilter", resolveBgColor(bgColorIdx, useLightTheme))
            }
            
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
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                views.setColorStateList(R.id.widget_outline, "setImageTintList", android.content.res.ColorStateList.valueOf(outlineColor))
            } else {
                views.setInt(R.id.widget_outline, "setColorFilter", outlineColor)
            }
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
                // Warm neutral surface for background (overrides custom bg color when dynamic is on)
                views.setColorStateList(R.id.widget_background, "setImageTintList", android.content.res.ColorStateList.valueOf(context.getColor(if (useLightTheme) android.R.color.system_neutral2_50 else android.R.color.system_neutral1_800)))
                // Accent-tinted outline
                if (showOutline) {
                    views.setColorStateList(R.id.widget_outline, "setImageTintList", android.content.res.ColorStateList.valueOf(context.getColor(if (useLightTheme) android.R.color.system_accent1_300 else android.R.color.system_accent1_400)))
                }
            }

            if (mode == UpdateMode.TICK) {
                val tickViews = RemoteViews(context.packageName, layoutId)
                val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
                    context.registerReceiver(null, ifilter)
                }
                val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 0
                val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
                val batteryPct = (level * 100 / scale.toFloat()).toInt()
                val batterySpannable = android.text.SpannableString("${batteryPct}%")
                batterySpannable.setSpan(android.text.style.RelativeSizeSpan(0.5f), batterySpannable.length - 1, batterySpannable.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                val tempInt = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
                val tempVal = tempInt / 10f
                if (showSteps) loadStepCount(context, tickViews, prefs)
                if (showBattery) tickViews.setTextViewText(R.id.text_battery, batterySpannable)
                if (showTemp) {
                    val tempStr = String.format("%.1f", tempVal)
                    val tempText = "$tempStr°C"
                    val tempSpan = android.text.SpannableString(tempText)
                    val cIdx = tempText.indexOf("°C")
                    if (cIdx != -1) {
                        tempSpan.setSpan(android.text.style.RelativeSizeSpan(0.5f), cIdx, cIdx + 2, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    if (boldTemp) tempSpan.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, tempSpan.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    tickViews.setTextViewText(R.id.text_temp, tempSpan)
                }
                if (showData) updateDataUsage(context, tickViews)
                if (showStorage) updateStorageStats(context, tickViews)
                appWidgetManager.partiallyUpdateAppWidget(appWidgetIds, tickViews)
                return
            } else if (mode == UpdateMode.CALENDAR_ONLY) {
                val calViews = RemoteViews(context.packageName, layoutId)
                if (showEvents) loadCalendarEvents(context, calViews, sizeEvents, primaryColor, secondaryColor)
                appWidgetManager.partiallyUpdateAppWidget(appWidgetIds, calViews)
                return
            } else if (mode == UpdateMode.TASKS_ONLY) {
                val taskViews = RemoteViews(context.packageName, layoutId)
                if (showTasks) loadTasks(context, taskViews, sizeTasks, primaryColor)
                appWidgetManager.partiallyUpdateAppWidget(appWidgetIds, taskViews)
                return
            } else if (mode == UpdateMode.ALARM_ONLY) {
                val alarmViews = RemoteViews(context.packageName, layoutId)
                if (showNextAlarm) loadNextAlarm(context, alarmViews, sizeNextAlarm, secondaryColor)
                appWidgetManager.partiallyUpdateAppWidget(appWidgetIds, alarmViews)
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
            
            views.setViewVisibility(R.id.text_temp, if (showTemp) android.view.View.VISIBLE else android.view.View.GONE)
            views.setTextViewTextSize(R.id.text_temp, android.util.TypedValue.COMPLEX_UNIT_SP, sizeTemp)
            views.setTextColor(R.id.text_battery, secondaryColor)
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
                val batterySpannable = android.text.SpannableString("${batteryPct}%")
                batterySpannable.setSpan(android.text.style.RelativeSizeSpan(0.5f), batterySpannable.length - 1, batterySpannable.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (boldBattery) batterySpannable.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, batterySpannable.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                views.setTextViewText(R.id.text_battery, batterySpannable)
            }
            if (showTemp) {
                val tempStr = String.format("%.1f", tempVal)
                val tempText = "$tempStr°C"
                val tempSpan = android.text.SpannableString(tempText)
                val cIdx = tempText.indexOf("°C")
                if (cIdx != -1) {
                    tempSpan.setSpan(android.text.style.RelativeSizeSpan(0.5f), cIdx, cIdx + 2, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                if (boldTemp) tempSpan.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, tempSpan.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                views.setTextViewText(R.id.text_temp, tempSpan)
            }
            
            // --- Weather Condition ---
            val showWeather = showWeatherCondition && bweather != null
            views.setViewVisibility(R.id.text_weather_condition, if (showWeather) android.view.View.VISIBLE else android.view.View.GONE)
            if (showWeather && bweather != null) {
                var weatherCode = bweather.currentConditionCode
                var weatherText = bweather.currentCondition
                var hasWarning = false
                
                // Check week forecasts for warnings
                val forecasts = bweather.forecasts
                if (forecasts != null && forecasts.isNotEmpty()) {
                    for ((index, forecast) in forecasts.take(7).withIndex()) {
                        val fCode = forecast.conditionCode
                        if (fCode != null && (
                            fCode in listOf(500, 501, 502, 503, 504, 511, 520, 521, 522, 531) || // Rain
                            fCode in listOf(600, 601, 602, 611, 612, 615, 616, 620, 621, 622) || // Snow
                            fCode in listOf(210, 211, 212, 221, 230, 231, 232) // Storm
                        )) {
                            hasWarning = true
                            weatherCode = fCode
                            
                            // Determine string representation of the day
                            val dayText = when (index) {
                                0 -> "today"
                                1 -> "tomorrow"
                                else -> {
                                    val localDate = java.time.LocalDate.now().plusDays(index.toLong())
                                    localDate.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
                                }
                            }
                            
                            // Extract probability and create warning string
                            val precipString = if (forecast.precipProbability != null && forecast.precipProbability > 0) "${forecast.precipProbability}% " else ""
                            val conditionWarning = when (fCode) {
                                in listOf(500, 501, 502, 503, 504, 511, 520, 521, 522, 531) -> if (index <= 1) "Rain $dayText" else "Rain on $dayText"
                                in listOf(600, 601, 602, 611, 612, 615, 616, 620, 621, 622) -> if (index <= 1) "Snow $dayText" else "Snow on $dayText"
                                in listOf(210, 211, 212, 221, 230, 231, 232) -> if (index <= 1) "Storm $dayText" else "Storm on $dayText"
                                else -> "Warning"
                            }
                            weatherText = "$precipString$conditionWarning"
                            break 
                        }
                    }
                }
                
                var conditionText = weatherText
                if (conditionText.isNullOrEmpty()) {
                    conditionText = "Unknown"
                }

                // Get weather icon string representation
                val weatherIcon = when (weatherCode) {
                    800 -> "☀️" // Clear
                    801, 802 -> "⛅" // Partly Cloudy
                    803, 804 -> "☁️" // Cloudy
                    500, 501, 502, 503, 504, 511, 520, 521, 522, 531 -> "🌧️" // Rain
                    600, 601, 602, 611, 612, 615, 616, 620, 621, 622 -> "❄️" // Snow
                    771 -> "🌬️" // Wind
                    741 -> "🌫️" // Fog
                    751 -> "🌁" // Haze
                    210, 211, 212, 221, 230, 231, 232 -> "⛈️" // Thunderstorm
                    else -> "" 
                }
                
                val displayString = if (showWeatherIconOnly && !hasWarning) weatherIcon else "$conditionText $weatherIcon"
                
                // If it has warning format like "Rain on Sun 🌧️", make " on Sun 🌧️" smaller
                if (hasWarning) {
                    val fullMatch = weatherText ?: "Unknown"
                    val span = android.text.SpannableString(displayString.trim())
                    
                    // The day portion is the last word for today/tomorrow, or the last two words for "on Sun"
                    val lastSpaceIdx = fullMatch.lastIndexOf(' ')
                    val onSpaceIdx = fullMatch.lastIndexOf(" on ")
                    
                    val shrinkStartIndex = if (onSpaceIdx != -1) onSpaceIdx else lastSpaceIdx
                    if (shrinkStartIndex != -1 && shrinkStartIndex < span.length) {
                        span.setSpan(android.text.style.RelativeSizeSpan(0.75f), shrinkStartIndex, span.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    } else if (weatherIcon.isNotEmpty()) {
                        span.setSpan(android.text.style.RelativeSizeSpan(0.75f), span.length - weatherIcon.length, span.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    if (boldWeather) span.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, span.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    views.setTextViewText(R.id.text_weather_condition, span)
                } else {
                    val weatherSpan = android.text.SpannableString(displayString.trim())
                    if (boldWeather) weatherSpan.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, weatherSpan.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    views.setTextViewText(R.id.text_weather_condition, weatherSpan)
                }
                views.setTextViewTextSize(R.id.text_weather_condition, android.util.TypedValue.COMPLEX_UNIT_SP, sizeWeather)
                views.setTextColor(R.id.text_weather_condition, secondaryColor)
                
                val launchIntent = context.packageManager.getLaunchIntentForPackage("org.breezyweather")
                if (launchIntent != null) {
                    val pendingIntent = android.app.PendingIntent.getActivity(
                        context, 0, launchIntent, 
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.text_weather_condition, pendingIntent)
                }
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
                updateStorageStats(context, views)
            }

            // --- Step Counter ---
            views.setViewVisibility(R.id.text_steps, if (showSteps) android.view.View.VISIBLE else android.view.View.GONE)
            if (showSteps) {
                views.setTextViewTextSize(R.id.text_steps, android.util.TypedValue.COMPLEX_UNIT_SP, sizeSteps)
                views.setTextColor(R.id.text_steps, secondaryColor)
                loadStepCount(context, views, prefs)
            }
            
            // --- Screen Time ---
            val showScreenTime = prefs.getBoolean("show_screen_time", false)
            val sizeScreenTime = prefs.getFloat("size_screen_time", 14f)
            views.setViewVisibility(R.id.text_screen_time, if (showScreenTime) android.view.View.VISIBLE else android.view.View.GONE)
            if (showScreenTime) {
                views.setTextViewTextSize(R.id.text_screen_time, android.util.TypedValue.COMPLEX_UNIT_SP, sizeScreenTime)
                views.setTextColor(R.id.text_screen_time, secondaryColor)
                updateScreenTime(context, views, prefs)
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

            // Right Side Stack: ordered by user preference
            data class StackEntry(val viewId: Int, val isVisible: Boolean, val size: Float, val key: String)

            val allRightItems = listOf(
                StackEntry(R.id.text_battery, showBattery, sizeBattery, "show_battery"),
                StackEntry(R.id.text_temp, showTemp, sizeTemp, "show_temp"),
                StackEntry(R.id.text_weather_condition, showWeather, sizeWeather, "show_weather_condition"),
                StackEntry(R.id.text_data_usage, showData, sizeData, "show_data_usage"),
                StackEntry(R.id.text_storage, showStorage, sizeStorage, "show_storage"),
                StackEntry(R.id.text_steps, showSteps, sizeSteps, "show_steps"),
                StackEntry(R.id.text_screen_time, showScreenTime, sizeScreenTime, "show_screen_time")
            )

            val savedOrder = prefs.getString("widget_right_column_order", "")
            val rightStack = if (savedOrder.isNullOrEmpty()) {
                allRightItems
            } else {
                val orderKeys = savedOrder.split(",")
                val ordered = orderKeys.mapNotNull { k -> allRightItems.find { it.key == k } }
                val remaining = allRightItems.filter { item -> item.key !in orderKeys }
                ordered + remaining
            }

            // Position items using explicit padding instead of layout_below
            // Calculate cumulative Y positions for each visible item
            val rightDp = context.resources.displayMetrics.density
            var cumulativeTopDp = 16f  // Starting top margin from top of widget
            for (entry in rightStack) {
                if (entry.isVisible) {
                    val topPaddingPx = (cumulativeTopDp * rightDp).toInt()
                    views.setViewPadding(entry.viewId, 0, topPaddingPx, 0, 0)
                    // Advance by this item's height + small gap
                    val itemHeightDp = entry.size * 1.2f  // approximate line height
                    cumulativeTopDp += itemHeightDp + 2f
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

            val dataIntent = Intent(android.provider.Settings.ACTION_DATA_USAGE_SETTINGS)
            val dataPendingIntent = PendingIntent.getActivity(context, 4, dataIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.text_data_usage, dataPendingIntent)

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

            appWidgetManager.updateAppWidget(appWidgetIds, views)
        }


        data class EventInfo(val id: Long, val title: String, val begin: Long, val isLocal: Boolean)

        private fun fetchCalendarEvents(context: Context): List<EventInfo> {
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
                val nameIdx = cursor.getColumnIndex(android.provider.CalendarContract.Calendars.ACCOUNT_NAME)
                val displayIdx = cursor.getColumnIndex(android.provider.CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val calId = cursor.getLong(idIdx)
                    val accountName = cursor.getString(nameIdx) ?: ""
                    val displayName = cursor.getString(displayIdx) ?: ""
                    
                    visibleCalendarIds.add(calId)

                    if (displayName.contains("holiday", ignoreCase = true) ||
                        accountName.contains("holiday", ignoreCase = true)) {
                        syncedCalendarIds.add(calId)
                    }
                }
            }
            
            if (visibleCalendarIds.isEmpty()) return emptyList()

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
            val selection = "${android.provider.CalendarContract.Instances.END} >= ? AND ${android.provider.CalendarContract.Instances.CALENDAR_ID} IN ($idList)"
            val selectionArgs = arrayOf(now.toString())
            val sortOrder = "${android.provider.CalendarContract.Instances.BEGIN} ASC"

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
            return events
        }

        private fun bindCalendarEvents(context: Context, views: RemoteViews, events: List<EventInfo>, textSizeSp: Float, primaryColor: Int, secondaryColor: Int, eventViews: List<Int>) {
            val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
            val dayFormatter = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
            val dateFormatter = DateTimeFormatter.ofPattern("d MMM h:mma", Locale.getDefault())

            if (events.isEmpty()) {
                views.setTextViewText(eventViews[0], "No events today")
                views.setTextColor(eventViews[0], secondaryColor)
                views.setTextViewTextSize(eventViews[0], android.util.TypedValue.COMPLEX_UNIT_SP, textSizeSp)
                views.setViewVisibility(eventViews[0], android.view.View.VISIBLE)
                
                val emptyIntent = PendingIntent.getActivity(context, 0, Intent(), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                views.setOnClickPendingIntent(eventViews[0], emptyIntent)

                for (i in 1 until eventViews.size) {
                    views.setViewVisibility(eventViews[i], android.view.View.GONE)
                }
                return
            }

            for (i in eventViews.indices) {
                if (i < events.size) {
                    val event = events[i]
                    val eventTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.begin), ZoneId.systemDefault())
                    val today = LocalDate.now()
                    val tomorrow = today.plusDays(1)
                    val oneWeekLater = today.plusWeeks(1)
                    
                    val timeText = if (eventTime.toLocalDate().isEqual(today)) {
                        "Today ${eventTime.format(timeFormatter)}"
                    } else if (eventTime.toLocalDate().isEqual(tomorrow)) {
                        "Tomorrow ${eventTime.format(timeFormatter)}"
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
                val events = fetchCalendarEvents(context)
                bindCalendarEvents(context, views, events, textSizeSp, primaryColor, secondaryColor, eventViews)
            } catch (e: Exception) {
                // Log and gracefully handle crash
                android.util.Log.e("LWidget", "Error loading calendar events", e)
                for (viewId in eventViews) {
                    views.setViewVisibility(viewId, android.view.View.GONE)
                }
            }
        }

        private fun updateScreenTime(context: Context, views: RemoteViews, prefs: android.content.SharedPreferences) {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
            val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                 appOps.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
            } else {
                 appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
            }
            if (mode != android.app.AppOpsManager.MODE_ALLOWED) {
                 views.setViewVisibility(R.id.text_screen_time, android.view.View.GONE)
                 return
            }

            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
            
            val calendar = java.util.Calendar.getInstance()
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
            calendar.set(java.util.Calendar.MINUTE, 0)
            calendar.set(java.util.Calendar.SECOND, 0)
            calendar.set(java.util.Calendar.MILLISECOND, 0)
            
            val startTime = calendar.timeInMillis
            val endTime = System.currentTimeMillis()
            
            val stats = usageStatsManager.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
            
            var totalForegroundTime = 0L
            if (stats != null) {
                for (usage in stats) {
                    // Only count significant foreground usage correctly reported
                    if (usage.totalTimeInForeground > 0) {
                         totalForegroundTime += usage.totalTimeInForeground
                    }
                }
            }
            
            val isBold = prefs.getBoolean("bold_screen_time", false)

            if (totalForegroundTime > 0) {
                val totalMinutes = totalForegroundTime / (1000 * 60)
                val hours = totalMinutes / 60
                val mins = totalMinutes % 60
                val timeString = if (hours > 0) "${hours}h ${mins}m \u23F3" else "${mins}m \u23F3" // ⏳
                val span = android.text.SpannableString(timeString)
                span.setSpan(android.text.style.RelativeSizeSpan(0.75f), span.length - 2, span.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (isBold) span.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, span.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                views.setTextViewText(R.id.text_screen_time, span)
            } else {
                val span = android.text.SpannableString("0m \u23F3")
                span.setSpan(android.text.style.RelativeSizeSpan(0.75f), span.length - 2, span.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (isBold) span.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, span.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                views.setTextViewText(R.id.text_screen_time, span)
            }
        }

        private fun updateDataUsage(context: Context, views: RemoteViews) {
            val prefs = context.getSharedPreferences("com.leanbitlab.lwidget.PREFS", Context.MODE_PRIVATE)
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
                val gb = mb / 1024f
                
                val text: CharSequence = if (gb >= 1.0f) {
                     val gbStr = String.format("%.2f", gb)
                     val span = android.text.SpannableString("$gbStr GB")
                     span.setSpan(android.text.style.RelativeSizeSpan(0.5f), gbStr.length, gbStr.length + 3, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) // GB
                     span
                } else {
                     val mbStr = String.format("%.1f", mb)
                     val span = android.text.SpannableString("$mbStr MB")
                     span.setSpan(android.text.style.RelativeSizeSpan(0.5f), mbStr.length, mbStr.length + 3, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) // MB
                     span
                }

                if (prefs.getBoolean("bold_data_usage", false) && text is android.text.SpannableString) {
                    text.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, text.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
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

        private data class TaskData(val title: String, val dueMillis: Long)

        private fun fetchActiveTasks(context: Context, limit: Int): List<TaskData> {
            val tasks = mutableListOf<TaskData>()
            val taskUri = android.net.Uri.parse("content://org.tasks/tasks")
            val selection = "completed=0 AND deleted=0"
            try {
                context.contentResolver.query(taskUri, null, selection, null, "dueDate ASC")?.use { cursor ->
                    val titleIdx = cursor.getColumnIndex("title")
                    val compIdx = cursor.getColumnIndex("completed")
                    val delIdx = cursor.getColumnIndex("deleted")
                    val dueIdx = cursor.getColumnIndex("dueDate")

                    if (titleIdx == -1) return emptyList()

                    while (cursor.moveToNext() && tasks.size < limit) {
                        val completed = if (compIdx >= 0) cursor.getString(compIdx) else null
                        val deleted = if (delIdx >= 0) cursor.getString(delIdx) else null
                        val dueMillis = if (dueIdx >= 0) cursor.getLong(dueIdx) else 0L

                        val isCompleted = completed != null && completed != "0"
                        val isDeleted = deleted != null && deleted != "0"

                        if (isCompleted || isDeleted) {
                            continue
                        }

                        val title = cursor.getString(titleIdx) ?: "No Title"
                        tasks.add(TaskData(title, dueMillis))
                    }
                }
            } catch (e: Exception) {
                // Return empty list on failure
            }
            return tasks
        }

        private fun formatDueSuffix(dueMillis: Long): String {
            if (dueMillis <= 0) return ""
            val dueDate = LocalDateTime.ofInstant(Instant.ofEpochMilli(dueMillis), ZoneId.systemDefault()).toLocalDate()
            val today = LocalDate.now()
            val tomorrow = today.plusDays(1)

            return if (dueDate.isBefore(today)) {
                " (Overdue)"
            } else if (dueDate.isEqual(today)) {
                " (Today)"
            } else if (dueDate.isEqual(tomorrow)) {
                " (Tomorrow)"
            } else {
                val df = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
                " (${dueDate.format(df)})"
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
            val hasPerm = context.checkSelfPermission(PERMISSION_READ_TASKS_ORG) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                          context.checkSelfPermission(PERMISSION_READ_TASKS_ASTRID) == android.content.pm.PackageManager.PERMISSION_GRANTED
            
            if (!hasPerm) {
                 views.setTextViewText(eventViews[0], "Missing Permission")
                 views.setViewVisibility(eventViews[0], android.view.View.VISIBLE)
                 for (j in 1 until eventViews.size) {
                     views.setViewVisibility(eventViews[j], android.view.View.GONE)
                 }
                 return
            }

            val tasks = fetchActiveTasks(context, eventViews.size)

            if (tasks.isEmpty()) {
                for (viewId in eventViews) {
                    views.setViewVisibility(viewId, android.view.View.GONE)
                }
                return
            }

            for (i in tasks.indices) {
                val task = tasks[i]
                val dueSuffix = formatDueSuffix(task.dueMillis)
                val fullText = "• ${task.title}$dueSuffix"
                val spannable = SpannableString(fullText)
                val accentColor = context.getColor(R.color.widget_outline)
                spannable.setSpan(ForegroundColorSpan(accentColor), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

                views.setTextViewText(eventViews[i], spannable)
                views.setTextColor(eventViews[i], primaryColor)
                views.setTextViewTextSize(eventViews[i], android.util.TypedValue.COMPLEX_UNIT_SP, textSizeSp)
                views.setViewVisibility(eventViews[i], android.view.View.VISIBLE)

                val taskIntent = context.packageManager.getLaunchIntentForPackage("org.tasks")
                if (taskIntent != null) {
                    val taskPendingIntent = PendingIntent.getActivity(context, 1000 + i, taskIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    views.setOnClickPendingIntent(eventViews[i], taskPendingIntent)
                }
            }

            for (j in tasks.size until eventViews.size) {
                views.setViewVisibility(eventViews[j], android.view.View.GONE)
            }
        }

        private fun loadWorldClock(views: RemoteViews, textSizeSp: Float, textColor: Int, zoneIdStr: String, is12Hour: Boolean) {
             try {
                 val zoneId = ZoneId.of(zoneIdStr)
                 val zdt = java.time.ZonedDateTime.now(zoneId)
                 val pattern = if (is12Hour) "h:mm a" else "H:mm"
                 val formatter = DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
                 val timeStr = zdt.format(formatter)

                 // Format: "🌍 10:30 AM"
                 val text = "\uD83C\uDF0D $timeStr"
                 views.setTextViewText(R.id.text_world_clock, text)
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

        private fun updateStorageStats(context: Context, views: RemoteViews) {
             val prefs = context.getSharedPreferences("com.leanbitlab.lwidget.PREFS", Context.MODE_PRIVATE)
             try {
                 val path = android.os.Environment.getDataDirectory()
                 val stat = android.os.StatFs(path.path)
                 val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
                 
                 val gb = freeBytes / (1024f * 1024f * 1024f)
                 
                 val gbStr = String.format("%.0f", gb)
                 val span = android.text.SpannableString("$gbStr GB")
                 span.setSpan(android.text.style.RelativeSizeSpan(0.5f), gbStr.length, gbStr.length + 3, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) // GB

                 if (prefs.getBoolean("bold_storage", false)) {
                     span.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, span.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                 }

                 views.setTextViewText(R.id.text_storage, span)
             } catch (e: Exception) {
                 views.setTextViewText(R.id.text_storage, "Err")
             }
        }

        private fun loadStepCount(context: Context, views: RemoteViews, prefs: android.content.SharedPreferences) {
            try {
                val totalSteps = prefs.getFloat("last_total_steps", 0f)
                val baselineSteps = prefs.getFloat("step_baseline", 0f)

                val dailySteps = (totalSteps - baselineSteps).toInt().coerceAtLeast(0)
                val span = android.text.SpannableString("$dailySteps \uD83D\uDC5F") // 👟 outline sneaker
                span.setSpan(android.text.style.RelativeSizeSpan(0.75f), span.length - 2, span.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                
                if (prefs.getBoolean("bold_steps", false)) {
                    span.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, span.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }

                views.setTextViewText(R.id.text_steps, span)
            } catch (e: Exception) {
                // Fallback display if an exception occurs
                views.setTextViewText(R.id.text_steps, "Err")
            }
        }

        private fun getBestIntent(context: Context, packages: List<String>, fallback: Intent): Intent {
            val pm = context.packageManager
            for (pkg in packages) {
                val intent = pm.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    return intent
                }
            }
            return fallback
        }
    }
}
