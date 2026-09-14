package com.leanbitlab.lwidget.render

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.leanbitlab.lwidget.AwidgetProvider
import com.leanbitlab.lwidget.ColorResolver
import com.leanbitlab.lwidget.FallbackPreferences
import com.leanbitlab.lwidget.MainActivity
import com.leanbitlab.lwidget.R
import com.leanbitlab.lwidget.UpdateMode
import com.leanbitlab.lwidget.calendar.CalendarFetcher
import com.leanbitlab.lwidget.stats.SystemStatsFetcher
import com.leanbitlab.lwidget.tasks.TasksFetcher
import java.util.concurrent.ConcurrentHashMap

object WidgetRenderer {

    private const val TAG = "WidgetRenderer"

    private data class CacheEntry(val intent: Intent, val timestamp: Long)
    private val intentCache = ConcurrentHashMap<String, CacheEntry>()
    private const val CACHE_TTL_MS = 60000L // 60 seconds TTL

    fun getBestIntent(context: Context, packages: List<String>, fallback: Intent): Intent {
        val cacheKey = packages.joinToString(",") + "|" + fallback.action
        val cached = intentCache[cacheKey]
        val now = SystemClock.elapsedRealtime()
        if (cached != null && (now - cached.timestamp < CACHE_TTL_MS)) {
            return Intent(cached.intent)
        }

        val pm = context.packageManager
        for (pkg in packages) {
            val intent = pm.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intentCache[cacheKey] = CacheEntry(Intent(intent), now)
                return intent
            }
        }
        intentCache[cacheKey] = CacheEntry(Intent(fallback), now)
        return fallback
    }

    private fun getLayout(fontIdx: Int): Int {
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

    fun buildRemoteViews(context: Context, appWidgetId: Int, mode: UpdateMode = UpdateMode.FULL): RemoteViews {
        val globalPrefs = context.getSharedPreferences("com.leanbitlab.lwidget.PREFS", Context.MODE_PRIVATE)
        val prefs = if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            val wPrefs = context.getSharedPreferences("com.leanbitlab.lwidget.PREFS_$appWidgetId", Context.MODE_PRIVATE)
            FallbackPreferences(wPrefs, globalPrefs)
        } else {
            globalPrefs
        }

        val showTime = prefs.getBoolean("show_time", true)
        val sizeTime = prefs.getFloat("size_time", 56f)

        val showDate = prefs.getBoolean("show_date", true)
        val sizeDate = prefs.getFloat("size_date", 16f)

        val showBattery = prefs.getBoolean("show_battery", true)
        val sizeBattery = prefs.getFloat("size_battery", 32f)

        val showTemp = prefs.getBoolean("show_temp", false)
        val sizeTemp = prefs.getFloat("size_temp", 18f)

        val showWeatherCondition = prefs.getBoolean("show_weather_condition", false)
        val sizeWeather = prefs.getFloat("size_weather", 18f)
        val boldWeather = prefs.getBoolean("bold_weather", false)

        var showEvents = prefs.getBoolean("show_events", false)
        if (showEvents && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            showEvents = false
        }
        val sizeEvents = prefs.getFloat("size_events", 14f)

        val bweather = if (showWeatherCondition) {
            com.leanbitlab.lwidget.weather.BreezyWeatherFetcher.fetchLocalWeather(context)
        } else null
        val showWeatherIconOnly = prefs.getBoolean("show_weather_icon_only", false)

        val useDynamicColors = prefs.getBoolean("use_dynamic_colors", true)

        val isSystemInNightMode = (android.content.res.Resources.getSystem().configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val themeMode = prefs.getInt("theme_mode", if (prefs.getBoolean("use_system_theme", true)) 0 else 2)

        val useLightTheme = when (themeMode) {
            0 -> !isSystemInNightMode
            1 -> true
            2 -> false
            else -> !isSystemInNightMode
        }

        val timeFormatIdx = prefs.getInt("time_format_idx", 0)
        val dateFormatIdx = prefs.getInt("date_format_idx", 0)

        var showData = prefs.getBoolean("show_data_usage", false)
        if (showData && !SystemStatsFetcher.hasUsageStatsPermission(context)) {
            showData = false
        }
        val sizeData = prefs.getFloat("size_data", 14f)

        val showWorldClock = prefs.getBoolean("show_world_clock", false)
        val sizeWorldClock = prefs.getFloat("size_world_clock", 18f)
        val worldClockZoneStr = prefs.getString("world_clock_zone_str", "UTC") ?: "UTC"

        val showNextAlarm = prefs.getBoolean("show_next_alarm", false)
        val sizeNextAlarm = prefs.getFloat("size_next_alarm", 16f)

        val showStorage = prefs.getBoolean("show_storage", false)
        val sizeStorage = prefs.getFloat("size_storage", 14f)

        val showRam = prefs.getBoolean("show_ram", false)
        val sizeRam = prefs.getFloat("size_ram", 14f)

        var showSteps = prefs.getBoolean("show_steps", false)
        if (showSteps && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val actPerm = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACTIVITY_RECOGNITION)
            if (actPerm != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                showSteps = false
            }
        }
        val sizeSteps = prefs.getFloat("size_steps", 14f)

        var showTasks = prefs.getBoolean("show_tasks", false)
        if (showTasks) {
            val hasPerm = context.checkSelfPermission(TasksFetcher.PERMISSION_READ_TASKS_ORG) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                    context.checkSelfPermission(TasksFetcher.PERMISSION_READ_TASKS_ASTRID) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasPerm) {
                showTasks = false
            }
        }
        val sizeTasks = prefs.getFloat("size_tasks", 14f)

        val fontStyle = prefs.getInt("font_style_idx", 0)
        val bgColorIdx = prefs.getInt("bg_color_idx", 0)
        val bgOpacity = prefs.getInt("bg_opacity", 0)
        val textColorPrimaryIdx = prefs.getInt("text_color_primary_idx", 0)
        val textColorSecondaryIdx = prefs.getInt("text_color_secondary_idx", 0)

        val layoutId = getLayout(fontStyle)

        // Branching for partial TICK/event updates
        if (mode == UpdateMode.TICK) {
            val tickViews = RemoteViews(context.packageName, layoutId)
            SystemStatsFetcher.updateBatteryAndTemp(context, tickViews, prefs, showBattery, showTemp)
            if (showSteps) SystemStatsFetcher.loadStepCount(tickViews, prefs)
            if (showData) SystemStatsFetcher.updateDataUsage(context, tickViews, prefs)
            if (showStorage) SystemStatsFetcher.updateStorageStats(tickViews, prefs)
            if (showRam) SystemStatsFetcher.updateRamStats(context, tickViews, prefs)
            return tickViews
        } else if (mode == UpdateMode.CALENDAR_ONLY) {
            val calViews = RemoteViews(context.packageName, layoutId)
            val primaryColor = ColorResolver.resolveColor(context, prefs, useDynamicColors, textColorPrimaryIdx, isPrimary = true, isLight = useLightTheme)
            val secondaryColor = ColorResolver.resolveColor(context, prefs, useDynamicColors, textColorSecondaryIdx, isPrimary = false, isLight = useLightTheme)
            if (showEvents) CalendarFetcher.loadCalendarEvents(context, calViews, sizeEvents, primaryColor, secondaryColor, prefs)
            return calViews
        } else if (mode == UpdateMode.TASKS_ONLY) {
            val taskViews = RemoteViews(context.packageName, layoutId)
            val primaryColor = ColorResolver.resolveColor(context, prefs, useDynamicColors, textColorPrimaryIdx, isPrimary = true, isLight = useLightTheme)
            if (showTasks) TasksFetcher.loadTasks(context, taskViews, sizeTasks, primaryColor)
            return taskViews
        } else if (mode == UpdateMode.ALARM_ONLY) {
            val alarmViews = RemoteViews(context.packageName, layoutId)
            val secondaryColor = ColorResolver.resolveColor(context, prefs, useDynamicColors, textColorSecondaryIdx, isPrimary = false, isLight = useLightTheme)
            if (showNextAlarm) SystemStatsFetcher.loadNextAlarm(context, alarmViews, sizeNextAlarm, secondaryColor, prefs, showDate || showWorldClock)
            return alarmViews
        }

        val views = RemoteViews(context.packageName, layoutId)

        // Background & Outline
        views.setImageViewResource(R.id.widget_background, R.drawable.widget_bg_fill)

        fun resolveBgColor(idx: Int, isLight: Boolean): Int {
            return when (idx) {
                0 -> if (isLight) Color.WHITE else context.getColor(R.color.widget_bg_dark)
                1 -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getColor(android.R.color.system_accent1_500)
                } else {
                    context.getColor(R.color.widget_fallback_cyan)
                }
                2 -> {
                    val r = prefs.getInt("bg_color_r", 255)
                    val g = prefs.getInt("bg_color_g", 255)
                    val b = prefs.getInt("bg_color_b", 255)
                    Color.rgb(r, g, b)
                }
                else -> if (isLight) Color.WHITE else context.getColor(R.color.widget_bg_dark)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setColorStateList(R.id.widget_background, "setImageTintList", ColorStateList.valueOf(resolveBgColor(bgColorIdx, useLightTheme)))
        } else {
            views.setInt(R.id.widget_background, "setColorFilter", resolveBgColor(bgColorIdx, useLightTheme))
        }

        val alpha255 = (bgOpacity * 255 / 100).toInt().coerceIn(0, 255)
        views.setInt(R.id.widget_background, "setImageAlpha", alpha255)

        fun resolveOutlineColor(idx: Int): Int {
            return when (idx) {
                0 -> context.getColor(R.color.widget_outline)
                1 -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getColor(android.R.color.system_accent1_500)
                } else {
                    context.getColor(R.color.widget_fallback_cyan)
                }
                2 -> {
                    val r = prefs.getInt("outline_color_r", 255)
                    val g = prefs.getInt("outline_color_g", 255)
                    val b = prefs.getInt("outline_color_b", 255)
                    Color.rgb(r, g, b)
                }
                else -> context.getColor(R.color.widget_outline)
            }
        }

        val outlineColorIdx = prefs.getInt("outline_color_idx", 0)
        val showOutline = prefs.getBoolean("show_outline", false)
        val outlineColor = resolveOutlineColor(outlineColorIdx)
        views.setImageViewResource(R.id.widget_outline, R.drawable.widget_bg_outline)
        views.setViewVisibility(R.id.widget_outline, if (showOutline) View.VISIBLE else View.GONE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setColorStateList(R.id.widget_outline, "setImageTintList", ColorStateList.valueOf(outlineColor))
        } else {
            views.setInt(R.id.widget_outline, "setColorFilter", outlineColor)
        }
        views.setInt(R.id.widget_outline, "setImageAlpha", 255)

        val primaryColor = ColorResolver.resolveColor(context, prefs, useDynamicColors, textColorPrimaryIdx, true, useLightTheme)
        val secondaryColor = ColorResolver.resolveColor(context, prefs, useDynamicColors, textColorSecondaryIdx, false, useLightTheme)

        val dateColorIdx = prefs.getInt("date_color_idx", 0)
        val dateColor = if (useDynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getColor(if (useLightTheme) android.R.color.system_accent2_700 else android.R.color.system_accent2_100)
        } else {
            when (dateColorIdx) {
                2 -> Color.rgb(prefs.getInt("date_color_r", 255), prefs.getInt("date_color_g", 255), prefs.getInt("date_color_b", 255))
                1 -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getColor(android.R.color.system_accent2_500) else context.getColor(R.color.widget_fallback_yellow)
                else -> if (useLightTheme) context.getColor(R.color.widget_date_light) else context.getColor(R.color.widget_date_dark)
            }
        }

        val alarmColor = if (useDynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getColor(if (useLightTheme) android.R.color.system_accent3_700 else android.R.color.system_accent3_100)
        } else {
            if (useLightTheme) context.getColor(R.color.widget_alarm_light) else context.getColor(R.color.widget_alarm_dark)
        }

        if (useDynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setColorStateList(R.id.widget_background, "setImageTintList", ColorStateList.valueOf(context.getColor(if (useLightTheme) android.R.color.system_neutral2_50 else android.R.color.system_neutral1_800)))
            if (showOutline) {
                views.setColorStateList(R.id.widget_outline, "setImageTintList", ColorStateList.valueOf(context.getColor(if (useLightTheme) android.R.color.system_accent1_300 else android.R.color.system_accent1_400)))
            }
        }

        // Apply Time
        val timeVisible = showTime || showWorldClock
        views.setViewVisibility(R.id.time_container, if (timeVisible) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.clock_time, if (showTime) View.VISIBLE else View.GONE)
        views.setTextViewTextSize(R.id.clock_time, android.util.TypedValue.COMPLEX_UNIT_SP, sizeTime)
        views.setTextColor(R.id.clock_time, primaryColor)

        val (timeFormat12, timeFormat24) = when (timeFormatIdx) {
            0 -> "h:mm" to "H:mm"
            1 -> "H:mm" to "H:mm"
            else -> "h:mm" to "H:mm"
        }
        views.setCharSequence(R.id.clock_time, "setFormat12Hour", timeFormat12)
        views.setCharSequence(R.id.clock_time, "setFormat24Hour", timeFormat24)

        if (showWorldClock) {
            SystemStatsFetcher.loadWorldClock(views, sizeWorldClock, secondaryColor, worldClockZoneStr, timeFormat12.contains("a"))
        }

        // Apply Date
        val dateVisible = showDate || showNextAlarm
        views.setViewVisibility(R.id.date_container, if (dateVisible) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.clock_date, if (showDate) View.VISIBLE else View.GONE)
        views.setTextViewTextSize(R.id.clock_date, android.util.TypedValue.COMPLEX_UNIT_SP, sizeDate)
        views.setTextColor(R.id.clock_date, dateColor)

        val (dateFormat12, dateFormat24) = when (dateFormatIdx) {
            0 -> "EEEE, MMMM dd" to "EEEE, MMMM dd"
            1 -> "EEE, MMM dd" to "EEE, MMM dd"
            2 -> "dd/MM/yyyy" to "dd/MM/yyyy"
            else -> "EEEE, MMMM dd" to "EEEE, MMMM dd"
        }
        views.setCharSequence(R.id.clock_date, "setFormat12Hour", dateFormat12)
        views.setCharSequence(R.id.clock_date, "setFormat24Hour", dateFormat24)

        // Apply Battery & Temp
        views.setViewVisibility(R.id.text_battery, if (showBattery) View.VISIBLE else View.GONE)
        views.setTextViewTextSize(R.id.text_battery, android.util.TypedValue.COMPLEX_UNIT_SP, sizeBattery)
        views.setViewVisibility(R.id.text_temp, if (showTemp) View.VISIBLE else View.GONE)
        views.setTextViewTextSize(R.id.text_temp, android.util.TypedValue.COMPLEX_UNIT_SP, sizeTemp)
        views.setTextColor(R.id.text_battery, secondaryColor)
        views.setTextColor(R.id.text_temp, secondaryColor)

        SystemStatsFetcher.updateBatteryAndTemp(context, views, prefs, showBattery, showTemp)

        // Weather Condition
        val showWeather = showWeatherCondition && bweather != null
        views.setViewVisibility(R.id.layout_weather_condition, if (showWeather) View.VISIBLE else View.GONE)
        if (showWeather && bweather != null) {
            var weatherCode = bweather.currentConditionCode
            var weatherText = bweather.currentCondition
            var hasWarning = false

            val forecasts = bweather.forecasts
            if (forecasts != null && forecasts.isNotEmpty()) {
                for ((index, forecast) in forecasts.take(7).withIndex()) {
                    val fCode = forecast.conditionCode
                    if (fCode != null && (
                                fCode in listOf(500, 501, 502, 503, 504, 511, 520, 521, 522, 531) ||
                                        fCode in listOf(600, 601, 602, 611, 612, 615, 616, 620, 621, 622) ||
                                        fCode in listOf(210, 211, 212, 221, 230, 231, 232)
                                )) {
                        hasWarning = true
                        weatherCode = fCode
                        val dayText = when (index) {
                            0 -> "today"
                            1 -> "tomorrow"
                            else -> {
                                val localDate = java.time.LocalDate.now().plusDays(index.toLong())
                                localDate.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
                            }
                        }
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

            val weatherDrawableRes = when (weatherCode) {
                800 -> R.drawable.ic_weather_sunny
                801, 802 -> R.drawable.ic_weather_partly_cloudy
                803, 804 -> R.drawable.ic_weather_cloudy
                in listOf(500, 501, 502, 503, 504, 511, 520, 521, 522, 531) -> R.drawable.ic_weather_rainy
                in listOf(600, 601, 602, 611, 612, 615, 616, 620, 621, 622) -> R.drawable.ic_weather_snowy
                771 -> R.drawable.ic_weather_windy
                741 -> R.drawable.ic_weather_foggy
                751 -> R.drawable.ic_weather_mist
                in listOf(210, 211, 212, 221, 230, 231, 232) -> R.drawable.ic_weather_thunderstorm
                else -> R.drawable.ic_weather_cloudy
            }

            views.setImageViewResource(R.id.icon_weather, weatherDrawableRes)
            views.setInt(R.id.icon_weather, "setColorFilter", secondaryColor)
            views.setViewVisibility(R.id.icon_weather, View.VISIBLE)

            if (showWeatherIconOnly && !hasWarning) {
                views.setViewVisibility(R.id.text_weather_condition, View.GONE)
            } else {
                views.setViewVisibility(R.id.text_weather_condition, View.VISIBLE)
                val weatherSpan = android.text.SpannableString(conditionText)
                if (hasWarning) {
                    val lastSpaceIdx = conditionText.lastIndexOf(' ')
                    val onSpaceIdx = conditionText.lastIndexOf(" on ")
                    val shrinkStartIndex = if (onSpaceIdx != -1) onSpaceIdx else lastSpaceIdx
                    if (shrinkStartIndex != -1 && shrinkStartIndex < weatherSpan.length) {
                        weatherSpan.setSpan(android.text.style.RelativeSizeSpan(0.75f), shrinkStartIndex, weatherSpan.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
                if (boldWeather) weatherSpan.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, weatherSpan.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                views.setTextViewText(R.id.text_weather_condition, weatherSpan)
                views.setTextViewTextSize(R.id.text_weather_condition, android.util.TypedValue.COMPLEX_UNIT_SP, sizeWeather)
                views.setTextColor(R.id.text_weather_condition, secondaryColor)
            }

            val launchIntent = context.packageManager.getLaunchIntentForPackage("org.breezyweather")
            if (launchIntent != null) {
                val pendingIntent = PendingIntent.getActivity(context, 0, launchIntent, PendingIntent.FLAG_IMMUTABLE)
                views.setOnClickPendingIntent(R.id.layout_weather_condition, pendingIntent)
                views.setOnClickPendingIntent(R.id.text_weather_condition, pendingIntent)
            }
        }

        // Data Usage
        views.setViewVisibility(R.id.text_data_usage, if (showData) View.VISIBLE else View.GONE)
        if (showData) {
            views.setTextViewTextSize(R.id.text_data_usage, android.util.TypedValue.COMPLEX_UNIT_SP, sizeData)
            views.setTextColor(R.id.text_data_usage, secondaryColor)
            SystemStatsFetcher.updateDataUsage(context, views, prefs)
        }

        // Storage
        views.setViewVisibility(R.id.text_storage, if (showStorage) View.VISIBLE else View.GONE)
        if (showStorage) {
            views.setTextViewTextSize(R.id.text_storage, android.util.TypedValue.COMPLEX_UNIT_SP, sizeStorage)
            views.setTextColor(R.id.text_storage, secondaryColor)
            SystemStatsFetcher.updateStorageStats(views, prefs)
        }

        // RAM
        views.setViewVisibility(R.id.text_ram, if (showRam) View.VISIBLE else View.GONE)
        if (showRam) {
            views.setTextViewTextSize(R.id.text_ram, android.util.TypedValue.COMPLEX_UNIT_SP, sizeRam)
            views.setTextColor(R.id.text_ram, secondaryColor)
            SystemStatsFetcher.updateRamStats(context, views, prefs)
        }

        // Step Counter
        views.setViewVisibility(R.id.layout_steps, if (showSteps) View.VISIBLE else View.GONE)
        if (showSteps) {
            views.setTextViewTextSize(R.id.text_steps, android.util.TypedValue.COMPLEX_UNIT_SP, sizeSteps)
            views.setTextColor(R.id.text_steps, secondaryColor)
            views.setInt(R.id.icon_steps, "setColorFilter", secondaryColor)
            SystemStatsFetcher.loadStepCount(views, prefs)
        }

        // Screen Time
        val showScreenTime = prefs.getBoolean("show_screen_time", false)
        val sizeScreenTime = prefs.getFloat("size_screen_time", 14f)
        views.setViewVisibility(R.id.layout_screen_time, if (showScreenTime) View.VISIBLE else View.GONE)
        if (showScreenTime) {
            views.setTextViewTextSize(R.id.text_screen_time, android.util.TypedValue.COMPLEX_UNIT_SP, sizeScreenTime)
            views.setTextColor(R.id.text_screen_time, secondaryColor)
            views.setInt(R.id.icon_screen_time, "setColorFilter", secondaryColor)
            SystemStatsFetcher.updateScreenTime(context, views, prefs)
        }

        // Dynamic Spacing Logic for Both Sides
        fun dpToPx(dp: Float): Int = (dp * context.resources.displayMetrics.density).toInt()

        val paddingVal = prefs.getFloat("widget_padding", 24f)
        val basePadding = dpToPx(paddingVal)
        views.setViewPadding(R.id.inner_container, basePadding, 0, basePadding, basePadding)

        // Left Side Padding
        if (showTime || showWorldClock) {
            val size = if (showTime) sizeTime else sizeWorldClock
            val intrinsicGap = size * 0.18f
            views.setViewPadding(R.id.time_container, 0, maxOf(0, dpToPx(paddingVal - intrinsicGap)), 0, 0)
            views.setViewPadding(R.id.date_container, 0, 0, 0, 0)
        } else if (showDate || showNextAlarm) {
            views.setViewPadding(R.id.time_container, 0, 0, 0, 0)
            val size = if (showDate) sizeDate else sizeNextAlarm
            val intrinsicGap = size * 0.18f
            views.setViewPadding(R.id.date_container, 0, maxOf(0, dpToPx(paddingVal - intrinsicGap)), 0, 0)
        } else {
            views.setViewPadding(R.id.time_container, 0, 0, 0, 0)
            views.setViewPadding(R.id.date_container, 0, 0, 0, 0)
        }

        val eventsVisible = showEvents || showTasks
        val leftHasContent = (showTime || showWorldClock || showDate || showNextAlarm)
        if (eventsVisible) {
            val topMargin = if (leftHasContent) dpToPx(8f) else {
                val size = if (showEvents) sizeEvents else sizeTasks
                val intrinsicGap = size * 0.18f
                maxOf(0, dpToPx(paddingVal - intrinsicGap))
            }
            views.setViewPadding(R.id.events_container, 0, topMargin, 0, 0)
        }

        // Right Side Stack
        data class StackEntry(val viewId: Int, val isVisible: Boolean, val size: Float, val key: String)

        val allRightItems = listOf(
            StackEntry(R.id.text_battery, showBattery, sizeBattery, "show_battery"),
            StackEntry(R.id.text_temp, showTemp, sizeTemp, "show_temp"),
            StackEntry(R.id.layout_weather_condition, showWeather, sizeWeather, "show_weather_condition"),
            StackEntry(R.id.text_data_usage, showData, sizeData, "show_data_usage"),
            StackEntry(R.id.text_storage, showStorage, sizeStorage, "show_storage"),
            StackEntry(R.id.text_ram, showRam, sizeRam, "show_ram"),
            StackEntry(R.id.layout_steps, showSteps, sizeSteps, "show_steps"),
            StackEntry(R.id.layout_screen_time, showScreenTime, sizeScreenTime, "show_screen_time")
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

        val rightDp = context.resources.displayMetrics.density
        var currentTextY = paddingVal
        var isFirstVisible = true
        for (entry in rightStack) {
            if (entry.isVisible) {
                val itemHeight = when {
                    entry.size >= 40f -> entry.size * 1.15f
                    entry.size >= 18f -> entry.size * 1.25f
                    else -> entry.size * 1.32f
                }
                val intrinsicTopTrim = if (isFirstVisible) entry.size * 0.15f else 0f
                val topPaddingDp = maxOf(0f, currentTextY - intrinsicTopTrim)
                if (isFirstVisible) {
                    isFirstVisible = false
                }
                val topPaddingPx = (topPaddingDp * rightDp).toInt()
                views.setViewPadding(entry.viewId, 0, topPaddingPx, 0, 0)
                currentTextY += itemHeight + 3f
            }
        }

        // Clock Click Action
        val selectedClockPkg = prefs.getString("clock_app_package", "default") ?: "default"
        val alarmIntent = if (selectedClockPkg != "default") {
            context.packageManager.getLaunchIntentForPackage(selectedClockPkg)
                ?: getBestIntent(context, listOf("com.android.deskclock", "com.google.android.deskclock", "com.simplemobiletools.clock", "org.fossify.clock"), Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS))
        } else {
            val clockPackages = listOf("com.android.deskclock", "com.google.android.deskclock", "com.simplemobiletools.clock", "org.fossify.clock")
            getBestIntent(context, clockPackages, Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS))
        }
        val alarmPendingIntent = PendingIntent.getActivity(context, 0, alarmIntent, PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.clock_time, alarmPendingIntent)

        // Calendar Click Action (Issue #55)
        val selectedCalPkg = prefs.getString("calendar_app_package", "default") ?: "default"
        val calendarPackages = listOf("org.fossify.calendar", "com.simplemobiletools.calendar", "com.google.android.calendar", "com.android.calendar")
        val baseCalIntent = Intent(Intent.ACTION_VIEW).apply {
            data = android.net.Uri.parse("content://com.android.calendar/time")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val calendarIntent = if (selectedCalPkg != "default") {
            context.packageManager.getLaunchIntentForPackage(selectedCalPkg)
                ?: getBestIntent(context, calendarPackages, baseCalIntent)
        } else {
            getBestIntent(context, calendarPackages, baseCalIntent)
        }
        val calendarPendingIntent = PendingIntent.getActivity(context, 1, calendarIntent, PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.clock_date, calendarPendingIntent)

        val batteryIntent = Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
        val batteryPendingIntent = PendingIntent.getActivity(context, 2, batteryIntent, PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.text_battery, batteryPendingIntent)
        views.setOnClickPendingIntent(R.id.text_temp, batteryPendingIntent)

        val storageIntent = Intent(android.provider.Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
        val storagePendingIntent = PendingIntent.getActivity(context, 3, storageIntent, PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.text_storage, storagePendingIntent)

        val ramPendingIntent = PendingIntent.getActivity(context, 10, Intent(android.provider.Settings.ACTION_INTERNAL_STORAGE_SETTINGS), PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.text_ram, ramPendingIntent)

        val dataIntent = Intent(android.provider.Settings.ACTION_DATA_USAGE_SETTINGS)
        val dataPendingIntent = PendingIntent.getActivity(context, 4, dataIntent, PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.text_data_usage, dataPendingIntent)

        // Events OR Tasks
        views.setViewVisibility(R.id.events_container, if (showEvents || showTasks) View.VISIBLE else View.GONE)
        if (showEvents) {
            CalendarFetcher.loadCalendarEvents(context, views, sizeEvents, primaryColor, secondaryColor, prefs)
        } else if (showTasks) {
            TasksFetcher.loadTasks(context, views, sizeTasks, primaryColor)
        }

        // Next Alarm
        views.setViewVisibility(R.id.layout_next_alarm, if (showNextAlarm) View.VISIBLE else View.GONE)
        if (showNextAlarm) {
            SystemStatsFetcher.loadNextAlarm(context, views, sizeNextAlarm, alarmColor, prefs, showDate || showWorldClock)
        }
        views.setOnClickPendingIntent(R.id.layout_next_alarm, alarmPendingIntent)

        val refreshIntent = Intent(context, AwidgetProvider::class.java).apply {
            action = SystemStatsFetcher.ACTION_BATTERY_UPDATE
        }
        val refreshPendingIntent = PendingIntent.getBroadcast(context, 10, refreshIntent, PendingIntent.FLAG_IMMUTABLE)

        if (showTasks) {
            val tasksIntent = context.packageManager.getLaunchIntentForPackage("org.tasks")
            if (tasksIntent != null) {
                val tasksPendingIntent = PendingIntent.getActivity(context, 11, tasksIntent, PendingIntent.FLAG_IMMUTABLE)
                views.setOnClickPendingIntent(R.id.events_container, tasksPendingIntent)
            } else {
                views.setOnClickPendingIntent(R.id.events_container, refreshPendingIntent)
            }
        } else {
            views.setOnClickPendingIntent(R.id.events_container, refreshPendingIntent)
        }

        val settingsIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = android.net.Uri.parse("lwidget://widget/$appWidgetId")
        }
        val settingsPendingIntent = PendingIntent.getActivity(context, appWidgetId, settingsIntent, PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widget_root, settingsPendingIntent)

        return views
    }

    fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, mode: UpdateMode = UpdateMode.FULL) {
        val views = buildRemoteViews(context, appWidgetId, mode)
        if (mode == UpdateMode.FULL) {
            appWidgetManager.updateAppWidget(appWidgetId, views)
        } else {
            appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
        }
    }
}
