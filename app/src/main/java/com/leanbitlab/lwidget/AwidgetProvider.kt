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

class AwidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // Use goAsync to prevent ANR during heavy updates
        val pendingResult = goAsync()
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (appWidgetId in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, appWidgetId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleAlarm(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelAlarm(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_BATTERY_UPDATE || intent.action == Intent.ACTION_BOOT_COMPLETED) {
            
            // Re-schedule alarm on boot or update request
            if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
                scheduleAlarm(context)
            }

            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisAppWidget = ComponentName(context, AwidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget)
            
            // Trigger update via onUpdate (which handles goAsync)
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }

    private fun scheduleAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AwidgetProvider::class.java).apply {
            action = ACTION_BATTERY_UPDATE
        }
        val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // Trigger every 60 seconds.
        alarmManager.setRepeating(
            AlarmManager.RTC,
            System.currentTimeMillis(),
            60000L, 
            pendingIntent
        )
    }

    private fun cancelAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AwidgetProvider::class.java).apply {
            action = ACTION_BATTERY_UPDATE
        }
        val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager.cancel(pendingIntent)
    }

    companion object {
        const val ACTION_BATTERY_UPDATE = "com.leanbitlab.lwidget.ACTION_BATTERY_UPDATE"

        // Suspended function called from Coroutine
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val prefs = context.getSharedPreferences("com.leanbitlab.lwidget.PREFS", Context.MODE_PRIVATE)

            // --- Load Preferences ---
            val showTime = prefs.getBoolean("show_time", true)
            val sizeTime = prefs.getFloat("size_time", 48f)
            
            val showDate = prefs.getBoolean("show_date", true)
            val sizeDate = prefs.getFloat("size_date", 14f)
            
            val showBattery = prefs.getBoolean("show_battery", true)
            val sizeBattery = prefs.getFloat("size_battery", 48f)
            
            val showTemp = prefs.getBoolean("show_temp", true)
            val sizeTemp = prefs.getFloat("size_temp", 18f)
            
            val showEvents = prefs.getBoolean("show_events", true)
            val sizeEvents = prefs.getFloat("size_events", 14f)

            val showOutline = prefs.getBoolean("show_outline", false)
            val useLightTheme = prefs.getBoolean("use_light_theme", false)
            val isTransparent = prefs.getBoolean("transparent_background", false)
            
            val timeFormatIdx = prefs.getInt("time_format_idx", 0)
            val dateFormatIdx = prefs.getInt("date_format_idx", 0)
            
            val showData = prefs.getBoolean("show_data_usage", false)
            val sizeData = prefs.getFloat("size_data", 14f)
            
            val showTasks = prefs.getBoolean("show_tasks", false)
            val sizeTasks = prefs.getFloat("size_tasks", 14f)

            val fontStyle = prefs.getInt("font_style", 0) 

            // --- Theme & Font Setup ---
            fun getLayout(baseLayoutId: Int, fontIdx: Int): Int {
                if (baseLayoutId == R.layout.widget_layout) {
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
                if (baseLayoutId == R.layout.widget_layout_transparent_dark) {
                     return when (fontIdx) {
                         1 -> R.layout.widget_layout_transparent_dark_serif
                         2 -> R.layout.widget_layout_transparent_dark_mono
                         3 -> R.layout.widget_layout_transparent_dark_cursive
                         4 -> R.layout.widget_layout_transparent_dark_condensed
                         5 -> R.layout.widget_layout_transparent_dark_condensed_light
                         6 -> R.layout.widget_layout_transparent_dark_light
                         7 -> R.layout.widget_layout_transparent_dark_medium
                         8 -> R.layout.widget_layout_transparent_dark_black
                         9 -> R.layout.widget_layout_transparent_dark_thin
                         10 -> R.layout.widget_layout_transparent_dark_smallcaps
                         else -> R.layout.widget_layout_transparent_dark
                     }
                }
                if (baseLayoutId == R.layout.widget_layout_transparent_light) {
                     return when (fontIdx) {
                         1 -> R.layout.widget_layout_transparent_light_serif
                         2 -> R.layout.widget_layout_transparent_light_mono
                         3 -> R.layout.widget_layout_transparent_light_cursive
                         4 -> R.layout.widget_layout_transparent_light_condensed
                         5 -> R.layout.widget_layout_transparent_light_condensed_light
                         6 -> R.layout.widget_layout_transparent_light_light
                         7 -> R.layout.widget_layout_transparent_light_medium
                         8 -> R.layout.widget_layout_transparent_light_black
                         9 -> R.layout.widget_layout_transparent_light_thin
                         10 -> R.layout.widget_layout_transparent_light_smallcaps
                         else -> R.layout.widget_layout_transparent_light
                     }
                }
                return baseLayoutId
            }

            val baseLayoutId = if (isTransparent) {
                if (useLightTheme) R.layout.widget_layout_transparent_light else R.layout.widget_layout_transparent_dark
            } else {
                R.layout.widget_layout
            }
            
            val layoutId = getLayout(baseLayoutId, fontStyle)

            val views = RemoteViews(context.packageName, layoutId)

            if (!isTransparent) {
                val bgRes = if (useLightTheme) {
                    if (showOutline) R.drawable.background_glow_light else R.drawable.background_light
                } else {
                    if (showOutline) R.drawable.background_glow else R.drawable.background_dark
                }
                views.setInt(R.id.widget_root, "setBackgroundResource", bgRes)
            }

            val primaryColor = if (isTransparent) {
                if (useLightTheme) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            } else {
                if (useLightTheme) context.getColor(R.color.widget_text_light) else android.graphics.Color.WHITE
            }

            val secondaryColor = if (isTransparent) {
                if (useLightTheme) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            } else {
                if (useLightTheme) context.getColor(R.color.widget_text_secondary_light) else android.graphics.Color.parseColor("#CCFFFFFF")
            }

            // --- Apply Time ---
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

            // --- Apply Date ---
            views.setViewVisibility(R.id.clock_date, if (showDate) android.view.View.VISIBLE else android.view.View.GONE)
            views.setTextViewTextSize(R.id.clock_date, android.util.TypedValue.COMPLEX_UNIT_SP, sizeDate)
            views.setTextColor(R.id.clock_date, secondaryColor)
            
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

            views.setTextViewText(R.id.text_battery, "$batteryPct%")
            views.setTextViewText(R.id.text_temp, "${String.format("%.1f", tempVal)}°C")
            
            // --- Data Usage ---
            views.setViewVisibility(R.id.text_data_usage, if (showData) android.view.View.VISIBLE else android.view.View.GONE)
            if (showData) {
                views.setTextViewTextSize(R.id.text_data_usage, android.util.TypedValue.COMPLEX_UNIT_SP, sizeData)
                views.setTextColor(R.id.text_data_usage, secondaryColor)
                updateDataUsage(context, views)
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

            // --- Calendar Events OR Tasks ---
            views.setViewVisibility(R.id.events_container, if (showEvents || showTasks) android.view.View.VISIBLE else android.view.View.GONE)
            
            if (showEvents) {
                loadCalendarEvents(context, views, sizeEvents, primaryColor, secondaryColor)
            } else if (showTasks) {
                loadTasks(context, views, sizeTasks, primaryColor, secondaryColor)
            }

            val refreshIntent = Intent(context, AwidgetProvider::class.java).apply {
                action = ACTION_BATTERY_UPDATE
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(context, 10, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.events_container, refreshPendingIntent)

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
                val typeIdx = cursor.getColumnIndex(android.provider.CalendarContract.Calendars.ACCOUNT_TYPE)
                val nameIdx = cursor.getColumnIndex(android.provider.CalendarContract.Calendars.ACCOUNT_NAME)
                val displayIdx = cursor.getColumnIndex(android.provider.CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val calId = cursor.getLong(idIdx)
                    val accountType = cursor.getString(typeIdx) ?: ""
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
            val selection = "${android.provider.CalendarContract.Instances.END} >= ? AND ${android.provider.CalendarContract.Instances.CALENDAR_ID} IN ($idList)"
            val selectionArgs = arrayOf(now.toString())
            val sortOrder = "${android.provider.CalendarContract.Instances.BEGIN} ASC"

            data class EventInfo(val title: String, val begin: Long, val isLocal: Boolean)
            val events = mutableListOf<EventInfo>()

            context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                val titleIdx = cursor.getColumnIndex(android.provider.CalendarContract.Events.TITLE)
                val beginIdx = cursor.getColumnIndex(android.provider.CalendarContract.Instances.BEGIN)
                val calIdIdx = cursor.getColumnIndex(android.provider.CalendarContract.Instances.CALENDAR_ID)

                while (cursor.moveToNext() && events.size < 10) {
                    val title = cursor.getString(titleIdx) ?: "No Title"
                    val begin = cursor.getLong(beginIdx)
                    val calId = cursor.getLong(calIdIdx)
                    val isLocal = !syncedCalendarIds.contains(calId)
                    events.add(EventInfo(title, begin, isLocal))
                }
            }

            // java.time formatter
            val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
            val dayFormatter = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())

            for (i in eventViews.indices) {
                if (i < events.size) {
                    val event = events[i]
                    val eventTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.begin), ZoneId.systemDefault())
                    val today = LocalDate.now()
                    
                    val timeText = if (eventTime.toLocalDate().isEqual(today)) {
                        eventTime.format(timeFormatter)
                    } else {
                        "${eventTime.format(dayFormatter)} ${eventTime.format(timeFormatter)}"
                    }
                    
                    val fullText = "• $timeText  ${event.title}"
                    val spannable = SpannableString(fullText)
                    val accentColor = context.getColor(R.color.widget_outline) 
                    spannable.setSpan(ForegroundColorSpan(accentColor), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    
                    views.setTextViewText(eventViews[i], spannable)
                    views.setTextColor(eventViews[i], if (event.isLocal) primaryColor else secondaryColor)
                    views.setTextViewTextSize(eventViews[i], android.util.TypedValue.COMPLEX_UNIT_SP, textSizeSp)
                    views.setViewVisibility(eventViews[i], android.view.View.VISIBLE)
                } else {
                    views.setViewVisibility(eventViews[i], android.view.View.GONE)
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
                     String.format("%.2f GB", mb / 1024f)
                } else {
                     String.format("%.1f MB", mb)
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

        private fun loadTasks(context: Context, views: RemoteViews, textSizeSp: Float, primaryColor: Int, secondaryColor: Int) {
            val eventViews = listOf(
                R.id.text_event_1, R.id.text_event_2, R.id.text_event_3,
                R.id.text_event_4, R.id.text_event_5, R.id.text_event_6,
                R.id.text_event_7, R.id.text_event_8, R.id.text_event_9,
                R.id.text_event_10
            )
            
            val taskUri = android.net.Uri.parse("content://org.tasks/tasks")
            val selection = "completed = 0" 
            
            try {
                context.contentResolver.query(taskUri, null, selection, null, "due ASC")?.use { cursor ->
                     val titleIdx = cursor.getColumnIndex("title")
                     
                     var i = 0
                     while (cursor.moveToNext() && i < eventViews.size) {
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
            }
            
            for (viewId in eventViews) {
                 views.setViewVisibility(viewId, android.view.View.GONE)
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
