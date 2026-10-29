package com.leanbitlab.lwidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import com.leanbitlab.lwidget.calendar.CalendarFetcher
import com.leanbitlab.lwidget.render.WidgetRenderer
import com.leanbitlab.lwidget.stats.SystemStatsFetcher
import com.leanbitlab.lwidget.tasks.TasksFetcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class UpdateMode {
    FULL, TICK, CALENDAR_ONLY, TASKS_ONLY, ALARM_ONLY, STEPS_ONLY
}

class AwidgetProvider : AppWidgetProvider() {

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        StepCounterService.startServiceIfEnabled(context)
        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                for (appWidgetId in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, appWidgetId, UpdateMode.FULL)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error during onUpdate", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        StepCounterService.startServiceIfEnabled(context)
        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                updateAppWidget(context, appWidgetManager, appWidgetId, UpdateMode.FULL)
            } catch (e: Throwable) {
                Log.e(TAG, "Error during onAppWidgetOptionsChanged", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleWork(context)
        StepCounterService.startServiceIfEnabled(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelWork(context)
        val prefs = context.getSharedPreferences("com.leanbitlab.lwidget.PREFS", Context.MODE_PRIVATE)
        val keepAlive = prefs.getBoolean("keep_alive", false)
        if (!keepAlive) {
            try {
                context.stopService(Intent(context, StepCounterService::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping StepCounterService onDisabled", e)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        val appWidgetManager = AppWidgetManager.getInstance(context)
        val thisAppWidget = ComponentName(context, AwidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget)

        val isBootOrReplaced = intent.action in listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON"
        )

        // Ensure service is launched immediately while boot FGS exemption is active
        if (isBootOrReplaced) {
            StepCounterService.startServiceIfEnabled(context)
        }

        if (isBootOrReplaced || intent.action in listOf(
                Intent.ACTION_CONFIGURATION_CHANGED,
                ACTION_BATTERY_UPDATE,
                StepCounterService.ACTION_STEP_UPDATE,
                Intent.ACTION_PROVIDER_CHANGED,
                AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED,
                "nodomain.freeyourgadget.gadgetbridge.ACTION_GENERIC_WEATHER"
            )) {
            val pendingResult = goAsync()
            receiverScope.launch {
                try {
                    when {
                        isBootOrReplaced || intent.action == Intent.ACTION_CONFIGURATION_CHANGED -> {
                            scheduleWork(context)
                            appWidgetIds.forEach { updateAppWidget(context, appWidgetManager, it, UpdateMode.FULL) }
                        }
                        intent.action == ACTION_BATTERY_UPDATE -> {
                            appWidgetIds.forEach { updateAppWidget(context, appWidgetManager, it, UpdateMode.TICK) }
                        }
                        intent.action == StepCounterService.ACTION_STEP_UPDATE -> {
                            appWidgetIds.forEach { updateAppWidget(context, appWidgetManager, it, UpdateMode.STEPS_ONLY) }
                        }
                        intent.action == AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED -> {
                            appWidgetIds.forEach { updateAppWidget(context, appWidgetManager, it, UpdateMode.ALARM_ONLY) }
                        }
                        intent.action == Intent.ACTION_PROVIDER_CHANGED -> {
                            val uri = intent.data
                            if (uri != null && uri.authority == "org.tasks") {
                                appWidgetIds.forEach { updateAppWidget(context, appWidgetManager, it, UpdateMode.TASKS_ONLY) }
                            } else {
                                appWidgetIds.forEach { updateAppWidget(context, appWidgetManager, it, UpdateMode.CALENDAR_ONLY) }
                            }
                        }
                        intent.action == "nodomain.freeyourgadget.gadgetbridge.ACTION_GENERIC_WEATHER" -> {
                            appWidgetIds.forEach { updateAppWidget(context, appWidgetManager, it, UpdateMode.FULL) }
                        }
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "Error handling broadcast ${intent.action}", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun scheduleWork(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AwidgetProvider::class.java).apply {
            action = ACTION_BATTERY_UPDATE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            500,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val prefs = context.getSharedPreferences("com.leanbitlab.lwidget.PREFS", Context.MODE_PRIVATE)
        val intervalMinutes = prefs.getFloat("update_interval", 15f)
        val intervalMillis = (intervalMinutes * 60f * 1000f).toLong().coerceAtLeast(60000L)

        alarmManager.setInexactRepeating(
            AlarmManager.RTC,
            System.currentTimeMillis() + intervalMillis,
            intervalMillis,
            pendingIntent
        )
    }

    private fun cancelWork(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AwidgetProvider::class.java).apply {
            action = ACTION_BATTERY_UPDATE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            500,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    companion object {

        private const val TAG = "WidgetLife"

        const val ACTION_NOOP = "com.leanbitlab.lwidget.ACTION_NOOP"
        const val ACTION_BATTERY_UPDATE = SystemStatsFetcher.ACTION_BATTERY_UPDATE
        const val PERMISSION_READ_TASKS_ORG = TasksFetcher.PERMISSION_READ_TASKS_ORG
        const val PERMISSION_READ_TASKS_ASTRID = TasksFetcher.PERMISSION_READ_TASKS_ASTRID

        data class EventInfo(
            val id: Long,
            val title: String,
            val begin: Long,
            val end: Long,
            val isLocal: Boolean,
            val isAllDay: Boolean = false
        )

        @Suppress("UnusedPrivateMember")
        private fun getFormatter(pattern: String) = CalendarFetcher.getFormatter(pattern)

        internal fun isEventRelevant(event: EventInfo, now: Long, today: LocalDate): Boolean {
            val mapped = CalendarFetcher.EventInfo(event.id, event.title, event.begin, event.end, event.isLocal, event.isAllDay)
            return CalendarFetcher.isEventRelevant(mapped, now, today)
        }

        internal fun formatEventTimeText(
            event: EventInfo,
            today: LocalDate = LocalDate.now(),
            showDayAbbr: Boolean = true
        ): String {
            val mapped = CalendarFetcher.EventInfo(event.id, event.title, event.begin, event.end, event.isLocal, event.isAllDay)
            return CalendarFetcher.formatEventTimeText(mapped, today, showDayAbbr)
        }

        internal fun calculateDailySteps(
            totalSteps: Float,
            baselineSteps: Float,
            savedDate: String,
            today: String = LocalDate.now().toString()
        ): Int = SystemStatsFetcher.calculateDailySteps(totalSteps, baselineSteps, savedDate, today)

        fun buildAppWidgetRemoteViews(context: Context, appWidgetId: Int, mode: UpdateMode = UpdateMode.FULL): RemoteViews =
            WidgetRenderer.buildRemoteViews(context, appWidgetId, mode)

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, mode: UpdateMode = UpdateMode.FULL) =
            WidgetRenderer.updateAppWidget(context, appWidgetManager, appWidgetId, mode)
    }
}
