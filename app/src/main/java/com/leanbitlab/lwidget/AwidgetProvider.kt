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
import android.os.BatteryManager
import android.widget.RemoteViews

class AwidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
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
        if (intent.action == ACTION_BATTERY_UPDATE) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisAppWidget = ComponentName(context, AwidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget)
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }

    private fun scheduleAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AwidgetProvider::class.java).apply {
            action = ACTION_BATTERY_UPDATE
        }
        val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // RTC (Type 1) does NOT wake the device.
        // It fires only if the device is awake (Screen On).
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
            val fontStyle = prefs.getInt("font_style", 0) 
            // 0=Def, 1=Serif, 2=Mono, 3=Cursive, 4=Cond, 5=CondLight, 6=Light, 7=Med, 8=Black, 9=Thin, 10=SmallCaps

            // --- Theme & Font Setup ---
            // Helper to get layout ID
            fun getLayout(baseLayoutId: Int, fontIdx: Int): Int {
                // If base is widget_layout
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
                // If base is transparent dark
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
                // If base is transparent light
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

            // Only set background if NOT transparent
            if (!isTransparent) {
                val bgRes = if (useLightTheme) {
                    if (showOutline) R.drawable.background_glow_light else R.drawable.background_light
                } else {
                    if (showOutline) R.drawable.background_glow else R.drawable.background_dark
                }
                views.setInt(R.id.widget_root, "setBackgroundResource", bgRes)
            }

            // Colors for manual text setting (though transparent layouts handle most via XML styles)
            // We still need these for dynamic updates if we were using same layout, but since we swap layouts, 
            // the XML attributes in transparent layouts (shadows, colors) handle static text.
            // However, we still programmatically set colors for consistecy in shared logic (like battery/events).
            
            // For Transparent:
            // Dark (White Text, Black Outline) -> Primary: White, Secondary: White
            // Light (Black Text, White Outline) -> Primary: Black, Secondary: Black
            
            // For Standard (Non-Transparent):
            // Dark -> Primary: White, Secondary: Light Gray
            // Light -> Primary: Black, Secondary: Dark Gray

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
            // In transparent mode, XML handles shadow/color better to ensure outline presence, 
            // but setting textColor here might override XML if not careful. 
            // RemoteViews.setTextColor REPLACES the color. It does NOT remove shadow.
            // So it is safe to set color here.
            views.setTextColor(R.id.clock_time, primaryColor)

            // --- Apply Date ---
            views.setViewVisibility(R.id.clock_date, if (showDate) android.view.View.VISIBLE else android.view.View.GONE)
            views.setTextViewTextSize(R.id.clock_date, android.util.TypedValue.COMPLEX_UNIT_SP, sizeDate)
            views.setTextColor(R.id.clock_date, secondaryColor)

            // --- Apply Battery & Temp ---
            views.setViewVisibility(R.id.text_battery, if (showBattery) android.view.View.VISIBLE else android.view.View.GONE)
            views.setTextViewTextSize(R.id.text_battery, android.util.TypedValue.COMPLEX_UNIT_SP, sizeBattery)
            views.setTextColor(R.id.text_battery, primaryColor)
            
            views.setViewVisibility(R.id.text_temp, if (showTemp) android.view.View.VISIBLE else android.view.View.GONE)
            views.setTextViewTextSize(R.id.text_temp, android.util.TypedValue.COMPLEX_UNIT_SP, sizeTemp)
            views.setTextColor(R.id.text_temp, secondaryColor)

            // --- Fetch & Update Data (Battery) ---
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
            
            // --- Click Actions ---
            
            // Time -> Try specific Clocks
            val clockPackages = listOf("com.android.deskclock", "com.google.android.deskclock", "com.simplemobiletools.clock", "org.fossify.clock")
            val alarmIntent = getBestIntent(context, clockPackages, Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS))
            val alarmPendingIntent = PendingIntent.getActivity(context, 0, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.clock_time, alarmPendingIntent)

            // Date -> Try specific Calendars
            val calendarPackages = listOf("org.fossify.calendar", "com.simplemobiletools.calendar", "com.google.android.calendar", "com.android.calendar")
            val baseCalIntent = Intent(Intent.ACTION_VIEW).apply { 
                data = android.net.Uri.parse("content://com.android.calendar/time") 
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val calendarIntent = getBestIntent(context, calendarPackages, baseCalIntent)
            val calendarPendingIntent = PendingIntent.getActivity(context, 1, calendarIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.clock_date, calendarPendingIntent)

            // Battery -> Battery Usage
            val batteryIntent = Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
            val batteryPendingIntent = PendingIntent.getActivity(context, 2, batteryIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.text_battery, batteryPendingIntent)
            views.setOnClickPendingIntent(R.id.text_temp, batteryPendingIntent)

            // --- Calendar Events ---
            views.setViewVisibility(R.id.events_container, if (showEvents) android.view.View.VISIBLE else android.view.View.GONE)
            if (showEvents) {
                loadCalendarEvents(context, views, sizeEvents, primaryColor, secondaryColor)
            }

            // Click on events container to refresh widget
            val refreshIntent = Intent(context, AwidgetProvider::class.java).apply {
                action = ACTION_BATTERY_UPDATE
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(context, 10, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.events_container, refreshPendingIntent)

            // Click on Root (Background) to open Widget Settings (MainActivity)
            val settingsIntent = Intent(context, MainActivity::class.java)
            val settingsPendingIntent = PendingIntent.getActivity(context, 0, settingsIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_root, settingsPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }


        private fun loadCalendarEvents(context: Context, views: RemoteViews, textSizeSp: Float, primaryColor: Int, secondaryColor: Int) {
            // Check permission
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

            // Get holiday/synced calendar IDs (non-local)
            val syncedCalendarIds = mutableSetOf<Long>()
            val visibleCalendarIds = mutableSetOf<Long>()
            
            // Only query visible calendars
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

                    // Mark as synced (holiday) if it contains "holiday" in name or display
                    // Everything else is considered "local" (personal)
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
            val endQuery = now + android.text.format.DateUtils.DAY_IN_MILLIS * 30 // 30 days ahead

            val uri = android.provider.CalendarContract.Instances.CONTENT_URI.buildUpon()
                .appendPath(now.toString())
                .appendPath(endQuery.toString())
                .build()

            // Filter for VISIBLE calendars
            // Use IN clause directly in selection
            val idList = visibleCalendarIds.joinToString(",")
            val selection = "${android.provider.CalendarContract.Instances.END} >= ? AND ${android.provider.CalendarContract.Instances.CALENDAR_ID} IN ($idList)"
            val selectionArgs = arrayOf(now.toString())
            val sortOrder = "${android.provider.CalendarContract.Instances.BEGIN} ASC"

            // Event data: title, begin, isLocal
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
                    // isLocal = NOT in synced calendars
                    val isLocal = !syncedCalendarIds.contains(calId)
                    events.add(EventInfo(title, begin, isLocal))
                }
            }

            // Populate event TextViews
            val timeFormat = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
            val dayFormat = java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault())

            for (i in eventViews.indices) {
                if (i < events.size) {
                    val event = events[i]
                    val timeText = if (android.text.format.DateUtils.isToday(event.begin)) {
                        timeFormat.format(java.util.Date(event.begin))
                    } else {
                        "${dayFormat.format(java.util.Date(event.begin))} ${timeFormat.format(java.util.Date(event.begin))}"
                    }
                    views.setTextViewText(eventViews[i], "• $timeText  ${event.title}")
                    views.setTextColor(eventViews[i], if (event.isLocal) primaryColor else secondaryColor)
                    views.setTextViewTextSize(eventViews[i], android.util.TypedValue.COMPLEX_UNIT_SP, textSizeSp)
                    views.setViewVisibility(eventViews[i], android.view.View.VISIBLE)
                } else {
                    views.setViewVisibility(eventViews[i], android.view.View.GONE)
                }
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
                    // Ignore and try next
                }
            }
            return fallback
        }
    }
}
