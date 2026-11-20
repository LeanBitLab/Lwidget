package com.leanbitlab.lwidget.stats

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import java.time.LocalDate
import java.time.ZoneId

/**
 * Today's app foreground time, counted from usage events since local midnight.
 *
 * queryUsageStats(INTERVAL_DAILY) returns whole daily buckets that are not aligned to local
 * midnight, so it counts part of yesterday. Events give exact intervals. To keep the per-minute
 * tick cheap, the running total is cached and only events since the last call are read.
 */
object ScreenTimeTracker {

    // UsageEvents.Event constants by value: some names need API 28/29, minSdk is 26
    private const val ACTIVITY_RESUMED = 1 // MOVE_TO_FOREGROUND
    private const val ACTIVITY_PAUSED = 2 // MOVE_TO_BACKGROUND
    private const val SCREEN_NON_INTERACTIVE = 16
    private const val DEVICE_SHUTDOWN = 26

    private const val DAY_MS = 86_400_000L
    private const val PREFS = "com.leanbitlab.lwidget.SCREEN_TIME" // i18n-ignore

    data class Event(val type: Int, val pkg: String, val time: Long)

    /** Running state for one day. [openPkg]/[openStart] describe the app in the foreground now. */
    data class State(
        val day: String,
        val processedUntil: Long,
        val totalMs: Long,
        val openPkg: String? = null,
        val openStart: Long = 0L
    ) {
        /** Total including the still-open interval up to [now]. */
        fun totalAt(now: Long): Long = totalMs + if (openPkg != null) (now - openStart).coerceAtLeast(0) else 0
    }

    /** Applies [events] (in time order) to [state]. Pure, so it is unit-tested directly. */
    fun fold(state: State, events: List<Event>, excludedPkg: String?): State {
        var total = state.totalMs
        var openPkg = state.openPkg
        var openStart = state.openStart
        fun close(at: Long) {
            if (openPkg != null) total += (at - openStart).coerceAtLeast(0)
            openPkg = null
        }
        for (e in events) {
            when (e.type) {
                ACTIVITY_RESUMED -> {
                    close(e.time)
                    if (e.pkg != excludedPkg) {
                        openPkg = e.pkg
                        openStart = e.time
                    }
                }
                ACTIVITY_PAUSED -> if (e.pkg == openPkg) close(e.time)
                SCREEN_NON_INTERACTIVE, DEVICE_SHUTDOWN -> close(e.time)
            }
        }
        return state.copy(totalMs = total, openPkg = openPkg, openStart = openStart)
    }

    /** Foreground time today in ms. Synchronized: several widgets may update at once. */
    @Synchronized
    fun todayForegroundMs(context: Context, now: Long = System.currentTimeMillis()): Long {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val midnight = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        var state = State(
            day = prefs.getString("day", "") ?: "",
            processedUntil = prefs.getLong("processed_until", 0L),
            totalMs = prefs.getLong("total_ms", 0L),
            openPkg = prefs.getString("open_pkg", null),
            openStart = prefs.getLong("open_start", 0L)
        )
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val launcher = defaultLauncher(context)
        if (state.day != today.toString() || state.processedUntil !in midnight..now) {
            // New day: replay the gap up to midnight so an app still open then counts from midnight
            val bridged = if (state.day.isNotEmpty() && state.processedUntil in (midnight - DAY_MS) until midnight) {
                fold(state, readEvents(usm, state.processedUntil, midnight), launcher).openPkg
            } else null
            state = State(today.toString(), midnight, 0L, bridged, midnight)
        }
        state = fold(state, readEvents(usm, state.processedUntil, now), launcher).copy(processedUntil = now)

        prefs.edit()
            .putString("day", state.day)
            .putLong("processed_until", state.processedUntil)
            .putLong("total_ms", state.totalMs)
            .putString("open_pkg", state.openPkg)
            .putLong("open_start", state.openStart)
            .apply()
        return state.totalAt(now)
    }

    private fun readEvents(usm: UsageStatsManager, from: Long, to: Long): List<Event> {
        val events = ArrayList<Event>()
        val iter = usm.queryEvents(from, to) ?: return events
        val e = UsageEvents.Event()
        while (iter.hasNextEvent()) {
            iter.getNextEvent(e)
            when (e.eventType) {
                ACTIVITY_RESUMED, ACTIVITY_PAUSED, SCREEN_NON_INTERACTIVE, DEVICE_SHUTDOWN ->
                    events.add(Event(e.eventType, e.packageName, e.timeStamp))
            }
        }
        return events
    }

    /** The home screen is not app usage (Digital Wellbeing excludes it too). */
    private fun defaultLauncher(context: Context): String? {
        val home = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME)
        return context.packageManager.resolveActivity(home, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
    }
}
