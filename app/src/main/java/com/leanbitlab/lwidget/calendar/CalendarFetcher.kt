package com.leanbitlab.lwidget.calendar

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.widget.RemoteViews
import com.leanbitlab.lwidget.MainActivity
import com.leanbitlab.lwidget.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object CalendarFetcher {

    data class EventInfo(
        val id: Long,
        val title: String,
        val begin: Long,
        val end: Long,
        val isLocal: Boolean,
        val isAllDay: Boolean = false
    )

    private var cachedLocale: Locale? = null
    private val formatters = ConcurrentHashMap<String, java.time.format.DateTimeFormatter>()

    internal fun getFormatter(pattern: String): java.time.format.DateTimeFormatter {
        val currentLocale = Locale.getDefault()
        if (cachedLocale != currentLocale) {
            cachedLocale = currentLocale
            formatters.clear()
        }
        return formatters.getOrPut(pattern) {
            java.time.format.DateTimeFormatter.ofPattern(pattern, currentLocale)
        }
    }

    internal fun isEventRelevant(event: EventInfo, now: Long, today: LocalDate): Boolean {
        return if (event.isAllDay) {
            val eventDate = Instant.ofEpochMilli(event.begin).atZone(ZoneOffset.UTC).toLocalDate()
            val eventEndDate = Instant.ofEpochMilli(event.end).atZone(ZoneOffset.UTC).toLocalDate()
            !eventDate.isBefore(today) || eventEndDate.isAfter(today)
        } else {
            event.end >= now
        }
    }

    internal fun formatEventTimeText(
        event: EventInfo,
        today: LocalDate = LocalDate.now(),
        showDayAbbr: Boolean = true
    ): String {
        val tomorrow = today.plusDays(1)
        val oneWeekLater = today.plusWeeks(1)

        val timeFormatter = getFormatter("h:mm")
        val dayTimeFormatter = getFormatter("EEE h:mm")
        val longDateFormatter = getFormatter("d MMM")
        val allDayNearFormatter = getFormatter("d/EEE")

        return if (event.isAllDay) {
            val eventDate = Instant.ofEpochMilli(event.begin).atZone(ZoneOffset.UTC).toLocalDate()
            val eventEndDate = Instant.ofEpochMilli(event.end).atZone(ZoneOffset.UTC).toLocalDate()
            val eventTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.begin), ZoneOffset.UTC)
            val isOngoingToday = eventDate.isEqual(today) || (eventDate.isBefore(today) && eventEndDate.isAfter(today))

            if (isOngoingToday) {
                "Today"
            } else if (eventDate.isEqual(tomorrow)) {
                "Tomorrow"
            } else if (eventDate.isAfter(today) && eventDate.isBefore(oneWeekLater)) {
                if (showDayAbbr) eventTime.format(allDayNearFormatter)
                else eventTime.format(getFormatter("d"))
            } else {
                eventTime.format(longDateFormatter)
            }
        } else {
            val eventTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.begin), ZoneId.systemDefault())
            val eventDate = eventTime.toLocalDate()
            if (eventDate.isEqual(today)) {
                "Today ${eventTime.format(timeFormatter)}"
            } else if (eventDate.isEqual(tomorrow)) {
                "Tomorrow ${eventTime.format(timeFormatter)}"
            } else if (eventDate.isBefore(oneWeekLater)) {
                if (showDayAbbr) "${eventTime.format(dayTimeFormatter)}"
                else eventTime.format(timeFormatter)
            } else {
                "${eventTime.format(longDateFormatter)} ${eventTime.format(timeFormatter)}"
            }
        }
    }

    fun fetchCalendarEvents(context: Context): List<EventInfo> {
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
            android.provider.CalendarContract.Instances.END,
            android.provider.CalendarContract.Instances.CALENDAR_ID,
            android.provider.CalendarContract.Instances.ALL_DAY
        )

        val now = System.currentTimeMillis()
        val today = LocalDate.now()
        val todayUtcStart = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val queryStart = minOf(now, todayUtcStart)
        val endQuery = now + android.text.format.DateUtils.DAY_IN_MILLIS * 30

        val uri = android.provider.CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(queryStart.toString())
            .appendPath(endQuery.toString())
            .build()

        val idList = visibleCalendarIds.joinToString(",")
        val selection = "${android.provider.CalendarContract.Instances.END} >= ? AND ${android.provider.CalendarContract.Instances.CALENDAR_ID} IN ($idList)"
        val selectionArgs = arrayOf(queryStart.toString())
        val sortOrder = "${android.provider.CalendarContract.Instances.BEGIN} ASC"

        val events = mutableListOf<EventInfo>()

        context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            val eventIdIdx = cursor.getColumnIndex(android.provider.CalendarContract.Instances.EVENT_ID)
            val titleIdx = cursor.getColumnIndex(android.provider.CalendarContract.Events.TITLE)
            val beginIdx = cursor.getColumnIndex(android.provider.CalendarContract.Instances.BEGIN)
            val endIdx = cursor.getColumnIndex(android.provider.CalendarContract.Instances.END)
            val calIdIdx = cursor.getColumnIndex(android.provider.CalendarContract.Instances.CALENDAR_ID)
            val allDayIdx = cursor.getColumnIndex(android.provider.CalendarContract.Instances.ALL_DAY)

            while (cursor.moveToNext() && events.size < 10) {
                val eventId = cursor.getLong(eventIdIdx)
                val title = cursor.getString(titleIdx) ?: "No Title"
                val begin = cursor.getLong(beginIdx)
                val end = cursor.getLong(endIdx)
                val calId = cursor.getLong(calIdIdx)
                val isLocal = !syncedCalendarIds.contains(calId)
                val isAllDay = allDayIdx >= 0 && cursor.getInt(allDayIdx) == 1
                val eventInfo = EventInfo(eventId, title, begin, end, isLocal, isAllDay)

                if (!isEventRelevant(eventInfo, now, today)) {
                    continue
                }
                events.add(eventInfo)
            }
        }
        return events
    }

    fun bindCalendarEvents(
        context: Context,
        views: RemoteViews,
        events: List<EventInfo>,
        textSizeSp: Float,
        primaryColor: Int,
        secondaryColor: Int,
        eventViews: List<Int>,
        prefs: SharedPreferences
    ) {
        val showDayAbbr = prefs.getBoolean("show_day_abbr_in_events", true)

        if (events.isEmpty()) {
            views.setTextViewText(eventViews[0], "No events today")
            views.setTextColor(eventViews[0], secondaryColor)
            views.setTextViewTextSize(eventViews[0], android.util.TypedValue.COMPLEX_UNIT_SP, textSizeSp)
            views.setViewVisibility(eventViews[0], android.view.View.VISIBLE)

            val emptyIntent = PendingIntent.getActivity(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(eventViews[0], emptyIntent)

            for (i in 1 until eventViews.size) {
                views.setViewVisibility(eventViews[i], android.view.View.GONE)
            }
            return
        }

        val today = LocalDate.now()
        for (i in eventViews.indices) {
            if (i < events.size) {
                val event = events[i]
                val timeText = formatEventTimeText(event, today, showDayAbbr)

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
                val eventPendingIntent = PendingIntent.getActivity(context, event.id.toInt(), eventIntent, PendingIntent.FLAG_IMMUTABLE)
                views.setOnClickPendingIntent(eventViews[i], eventPendingIntent)
            } else {
                views.setViewVisibility(eventViews[i], android.view.View.GONE)
            }
        }
    }

    fun loadCalendarEvents(
        context: Context,
        views: RemoteViews,
        textSizeSp: Float,
        primaryColor: Int,
        secondaryColor: Int,
        prefs: SharedPreferences
    ) {
        val eventViews = listOf(
            R.id.text_event_1, R.id.text_event_2, R.id.text_event_3,
            R.id.text_event_4, R.id.text_event_5, R.id.text_event_6,
            R.id.text_event_7, R.id.text_event_8, R.id.text_event_9,
            R.id.text_event_10
        )

        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.READ_CALENDAR
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            // Interactive permission prompt in widget
            views.setTextViewText(eventViews[0], "• Tap to grant calendar permission")
            views.setTextColor(eventViews[0], secondaryColor)
            views.setTextViewTextSize(eventViews[0], android.util.TypedValue.COMPLEX_UNIT_SP, textSizeSp)
            views.setViewVisibility(eventViews[0], android.view.View.VISIBLE)

            val settingsIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(context, 1001, settingsIntent, PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(eventViews[0], pendingIntent)

            for (i in 1 until eventViews.size) {
                views.setViewVisibility(eventViews[i], android.view.View.GONE)
            }
            return
        }

        try {
            val events = fetchCalendarEvents(context)
            bindCalendarEvents(context, views, events, textSizeSp, primaryColor, secondaryColor, eventViews, prefs)
        } catch (e: Exception) {
            android.util.Log.e("LWidget", "Error loading calendar events", e)
            for (viewId in eventViews) {
                views.setViewVisibility(viewId, android.view.View.GONE)
            }
        }
    }
}
