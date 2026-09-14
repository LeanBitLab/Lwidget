package com.leanbitlab.lwidget.stats

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.usage.NetworkStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Typeface
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.text.Spannable
import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.RemoteViews
import com.leanbitlab.lwidget.R
import com.leanbitlab.lwidget.calendar.CalendarFetcher
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

object SystemStatsFetcher {

    const val ACTION_BATTERY_UPDATE = "com.leanbitlab.lwidget.ACTION_BATTERY_UPDATE"

    private var lastUsageStatsCheckTime = 0L
    private var lastUsageStatsResult = false
    private const val USAGE_STATS_CACHE_TTL = 60000L

    fun hasUsageStatsPermission(context: Context): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastUsageStatsCheckTime < USAGE_STATS_CACHE_TTL) {
            return lastUsageStatsResult
        }

        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }

        lastUsageStatsResult = (mode == AppOpsManager.MODE_ALLOWED)
        lastUsageStatsCheckTime = now
        return lastUsageStatsResult
    }

    fun updateBatteryAndTemp(
        context: Context,
        views: RemoteViews,
        prefs: SharedPreferences,
        showBattery: Boolean,
        showTemp: Boolean
    ) {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            context.registerReceiver(null, ifilter)
        }
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 0
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
        val batteryPct = (level * 100 / scale.toFloat()).toInt()

        if (showBattery) {
            val batterySpannable = SpannableString("${batteryPct}%")
            batterySpannable.setSpan(
                RelativeSizeSpan(0.5f),
                batterySpannable.length - 1,
                batterySpannable.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            val boldBattery = prefs.getBoolean("bold_battery", true)
            if (boldBattery) {
                batterySpannable.setSpan(
                    StyleSpan(Typeface.BOLD),
                    0,
                    batterySpannable.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            views.setTextViewText(R.id.text_battery, batterySpannable)
        }

        if (showTemp) {
            val tempInt = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            var tempVal = tempInt / 10f
            val isFahrenheit = prefs.getInt("temp_unit_idx", 0) == 1
            val unitStr = if (isFahrenheit) "°F" else "°C"
            if (isFahrenheit) {
                tempVal = (tempVal * 9f / 5f) + 32f
            }
            val tempStr = String.format("%.1f", tempVal)
            val tempText = "$tempStr$unitStr"
            val tempSpan = SpannableString(tempText)
            val cIdx = tempText.indexOf(unitStr)
            if (cIdx != -1) {
                tempSpan.setSpan(
                    RelativeSizeSpan(0.5f),
                    cIdx,
                    cIdx + 2,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            val boldTemp = prefs.getBoolean("bold_temp", false)
            if (boldTemp) {
                tempSpan.setSpan(
                    StyleSpan(Typeface.BOLD),
                    0,
                    tempSpan.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            views.setTextViewText(R.id.text_temp, tempSpan)
        }
    }

    fun updateScreenTime(context: Context, views: RemoteViews, prefs: SharedPreferences) {
        if (!hasUsageStatsPermission(context)) {
            views.setViewVisibility(R.id.layout_screen_time, View.GONE)
            return
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)

        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)

        var totalForegroundTime = 0L
        if (stats != null) {
            for (usage in stats) {
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
            val timeString = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
            val span = SpannableString(timeString)
            if (isBold) span.setSpan(StyleSpan(Typeface.BOLD), 0, span.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            views.setTextViewText(R.id.text_screen_time, span)
        } else {
            val span = SpannableString("0m")
            if (isBold) span.setSpan(StyleSpan(Typeface.BOLD), 0, span.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            views.setTextViewText(R.id.text_screen_time, span)
        }
    }

    fun updateDataUsage(context: Context, views: RemoteViews, prefs: SharedPreferences) {
        val networkStatsManager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
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
                val span = SpannableString("$gbStr GB")
                span.setSpan(RelativeSizeSpan(0.5f), gbStr.length, gbStr.length + 3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                span
            } else {
                val mbStr = String.format("%.1f", mb)
                val span = SpannableString("$mbStr MB")
                span.setSpan(RelativeSizeSpan(0.5f), mbStr.length, mbStr.length + 3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                span
            }

            if (prefs.getBoolean("bold_data_usage", false) && text is SpannableString) {
                text.setSpan(StyleSpan(Typeface.BOLD), 0, text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            views.setTextViewText(R.id.text_data_usage, text)

        } catch (e: SecurityException) {
            val res = context.resources
            views.setTextViewText(R.id.text_data_usage, res.getString(R.string.no_perm))
        } catch (e: Exception) {
            val res = context.resources
            views.setTextViewText(R.id.text_data_usage, res.getString(R.string.error))
        }
    }

    fun updateStorageStats(views: RemoteViews, prefs: SharedPreferences) {
        try {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            val freeBytes = stat.availableBlocksLong * stat.blockSizeLong

            val gb = freeBytes / (1024f * 1024f * 1024f)

            val gbStr = String.format("%.0f", gb)
            val span = SpannableString("$gbStr GB")
            span.setSpan(RelativeSizeSpan(0.5f), gbStr.length, gbStr.length + 3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            if (prefs.getBoolean("bold_storage", false)) {
                span.setSpan(StyleSpan(Typeface.BOLD), 0, span.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            views.setTextViewText(R.id.text_storage, span)
        } catch (e: Exception) {
            views.setTextViewText(R.id.text_storage, "Err")
        }
    }

    fun updateRamStats(context: Context, views: RemoteViews, prefs: SharedPreferences) {
        try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            val freeBytes = memoryInfo.availMem
            val totalBytes = memoryInfo.totalMem

            val percentage = if (totalBytes > 0) (freeBytes.toFloat() / totalBytes.toFloat() * 100) else 0f

            val pctStr = String.format("%.0f", percentage)
            val span = SpannableString("$pctStr%")
            span.setSpan(RelativeSizeSpan(0.5f), pctStr.length, pctStr.length + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            if (prefs.getBoolean("bold_ram", false)) {
                span.setSpan(StyleSpan(Typeface.BOLD), 0, span.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            views.setTextViewText(R.id.text_ram, span)
        } catch (e: Exception) {
            views.setTextViewText(R.id.text_ram, "Err")
        }
    }

    fun calculateDailySteps(
        totalSteps: Float,
        baselineSteps: Float,
        savedDate: String,
        today: String = LocalDate.now().toString()
    ): Int {
        if (savedDate.isNotEmpty() && savedDate != today) {
            return 0
        }
        return (totalSteps - baselineSteps).toInt().coerceAtLeast(0)
    }

    fun loadStepCount(views: RemoteViews, prefs: SharedPreferences) {
        try {
            val totalSteps = prefs.getFloat("last_total_steps", 0f)
            val baselineSteps = prefs.getFloat("step_baseline", 0f)
            val savedDate = prefs.getString("step_date", "") ?: ""

            val dailySteps = calculateDailySteps(totalSteps, baselineSteps, savedDate)
            val span = SpannableString("$dailySteps")

            if (prefs.getBoolean("bold_steps", false)) {
                span.setSpan(StyleSpan(Typeface.BOLD), 0, span.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            views.setTextViewText(R.id.text_steps, span)
        } catch (e: Exception) {
            views.setTextViewText(R.id.text_steps, "Err")
        }
    }

    fun loadWorldClock(views: RemoteViews, textSizeSp: Float, textColor: Int, zoneIdStr: String, is12Hour: Boolean) {
        try {
            val zoneId = ZoneId.of(zoneIdStr)
            val zdt = ZonedDateTime.now(zoneId)
            val pattern = if (is12Hour) "h:mm a" else "H:mm"
            val formatter = CalendarFetcher.getFormatter(pattern)
            val timeStr = zdt.format(formatter)

            views.setTextViewText(R.id.text_world_clock, timeStr)
            views.setTextViewTextSize(R.id.text_world_clock, android.util.TypedValue.COMPLEX_UNIT_SP, textSizeSp)
            views.setTextColor(R.id.text_world_clock, textColor)
            views.setInt(R.id.icon_world_clock, "setColorFilter", textColor)
            views.setViewVisibility(R.id.layout_world_clock, View.VISIBLE)

        } catch (e: Exception) {
            views.setViewVisibility(R.id.layout_world_clock, View.GONE)
        }
    }

    fun loadNextAlarm(
        context: Context,
        views: RemoteViews,
        textSizeSp: Float,
        textColor: Int,
        prefs: SharedPreferences,
        hasPrecedingDate: Boolean = true
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val nextAlarm = alarmManager.nextAlarmClock

        if (nextAlarm != null) {
            val nextAlarmTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(nextAlarm.triggerTime), ZoneId.systemDefault())
            val timeFormatIdx = prefs.getInt("time_format_idx", 0)
            val is24Hour = timeFormatIdx == 1 || (timeFormatIdx == 0 && android.text.format.DateFormat.is24HourFormat(context))
            val timeFormatter = if (is24Hour) CalendarFetcher.getFormatter("H:mm") else CalendarFetcher.getFormatter("h:mm a")
            val timeText = nextAlarmTime.format(timeFormatter)

            views.setTextViewText(R.id.text_next_alarm, timeText)
            views.setTextViewTextSize(R.id.text_next_alarm, android.util.TypedValue.COMPLEX_UNIT_SP, textSizeSp)
            views.setTextColor(R.id.text_next_alarm, textColor)
            views.setTextViewTextSize(R.id.text_alarm_divider, android.util.TypedValue.COMPLEX_UNIT_SP, textSizeSp)
            views.setTextColor(R.id.text_alarm_divider, textColor)
            views.setInt(R.id.icon_next_alarm, "setColorFilter", textColor)
            views.setViewVisibility(R.id.text_alarm_divider, if (hasPrecedingDate) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.layout_next_alarm, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.layout_next_alarm, View.GONE)
        }
    }
}
