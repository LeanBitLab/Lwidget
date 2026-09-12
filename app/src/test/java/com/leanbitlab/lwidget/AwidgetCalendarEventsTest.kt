package com.leanbitlab.lwidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
class AwidgetCalendarEventsTest {

    @Test
    fun testAllDayEventInNegativeTimezoneShowsCorrectDate() {
        val originalTz = TimeZone.getDefault()
        try {
            // Simulate New York (UTC-4 in daylight saving)
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))

            val targetDate = LocalDate.of(2026, 9, 15)
            // Android stores all-day events at midnight UTC
            val beginUtcMillis = targetDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            val endUtcMillis = targetDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

            val event = AwidgetProvider.Companion.EventInfo(
                id = 1L,
                title = "Conference",
                begin = beginUtcMillis,
                end = endUtcMillis,
                isLocal = true,
                isAllDay = true
            )

            // 1. On the day of the event (Sept 15), it must show "Today"
            val textToday = AwidgetProvider.Companion.formatEventTimeText(
                event = event,
                today = LocalDate.of(2026, 9, 15),
                showDayAbbr = true
            )
            assertEquals("Today", textToday)

            // 2. On the day before the event (Sept 14), it must show "Tomorrow"
            val textTomorrow = AwidgetProvider.Companion.formatEventTimeText(
                event = event,
                today = LocalDate.of(2026, 9, 14),
                showDayAbbr = true
            )
            assertEquals("Tomorrow", textTomorrow)

            // 3. Five days before the event (Sept 10), it must show the day ("15/Tue")
            val textNear = AwidgetProvider.Companion.formatEventTimeText(
                event = event,
                today = LocalDate.of(2026, 9, 10),
                showDayAbbr = true
            )
            assertEquals("15/Tue", textNear)
        } finally {
            TimeZone.setDefault(originalTz)
        }
    }

    @Test
    fun testAllDayEventInPositiveTimezoneShowsCorrectDate() {
        val originalTz = TimeZone.getDefault()
        try {
            // Simulate Tokyo (UTC+9)
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))

            val targetDate = LocalDate.of(2026, 9, 15)
            val beginUtcMillis = targetDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            val endUtcMillis = targetDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

            val event = AwidgetProvider.Companion.EventInfo(
                id = 2L,
                title = "National Holiday",
                begin = beginUtcMillis,
                end = endUtcMillis,
                isLocal = true,
                isAllDay = true
            )

            val textToday = AwidgetProvider.Companion.formatEventTimeText(
                event = event,
                today = LocalDate.of(2026, 9, 15),
                showDayAbbr = true
            )
            assertEquals("Today", textToday)

            val textTomorrow = AwidgetProvider.Companion.formatEventTimeText(
                event = event,
                today = LocalDate.of(2026, 9, 14),
                showDayAbbr = true
            )
            assertEquals("Tomorrow", textTomorrow)
        } finally {
            TimeZone.setDefault(originalTz)
        }
    }

    @Test
    fun testOngoingMultiDayAllDayEvent() {
        val startDate = LocalDate.of(2026, 9, 14)
        val endDate = LocalDate.of(2026, 9, 18)
        val beginUtc = startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val endUtc = endDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        val event = AwidgetProvider.Companion.EventInfo(
            id = 3L,
            title = "Vacation",
            begin = beginUtc,
            end = endUtc,
            isLocal = true,
            isAllDay = true
        )

        // On Sept 15, event is ongoing: should show "Today"
        val textMidway = AwidgetProvider.Companion.formatEventTimeText(
            event = event,
            today = LocalDate.of(2026, 9, 15)
        )
        assertEquals("Today", textMidway)

        // Relevance check
        assertTrue(AwidgetProvider.Companion.isEventRelevant(event, System.currentTimeMillis(), LocalDate.of(2026, 9, 15)))
        assertTrue(AwidgetProvider.Companion.isEventRelevant(event, System.currentTimeMillis(), LocalDate.of(2026, 9, 17)))
        assertFalse(AwidgetProvider.Companion.isEventRelevant(event, System.currentTimeMillis(), LocalDate.of(2026, 9, 18)))
    }

    @Test
    fun testEventRelevanceFiltering() {
        val today = LocalDate.of(2026, 9, 15)
        val now = today.atTime(21, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() // 9 PM local

        // 1. Single-day all day event for today
        val todayAllDay = AwidgetProvider.Companion.EventInfo(
            id = 10L,
            title = "Today All-Day",
            begin = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            end = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            isLocal = true,
            isAllDay = true
        )
        assertTrue("Today's all day event must remain relevant even at 9 PM local",
            AwidgetProvider.Companion.isEventRelevant(todayAllDay, now, today))

        // 2. Single-day all day event for yesterday
        val yesterday = today.minusDays(1)
        val yesterdayAllDay = AwidgetProvider.Companion.EventInfo(
            id = 11L,
            title = "Yesterday All-Day",
            begin = yesterday.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            end = yesterday.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            isLocal = true,
            isAllDay = true
        )
        assertFalse("Yesterday's all day event must not be relevant today",
            AwidgetProvider.Companion.isEventRelevant(yesterdayAllDay, now, today))

        // 3. Timed event that already ended
        val endedTimed = AwidgetProvider.Companion.EventInfo(
            id = 12L,
            title = "Lunch Meeting",
            begin = now - 3600_000 * 2,
            end = now - 3600_000,
            isLocal = true,
            isAllDay = false
        )
        assertFalse("Past timed event must not be relevant",
            AwidgetProvider.Companion.isEventRelevant(endedTimed, now, today))

        // 4. Timed event in progress
        val currentTimed = AwidgetProvider.Companion.EventInfo(
            id = 13L,
            title = "Ongoing Meeting",
            begin = now - 1800_000,
            end = now + 1800_000,
            isLocal = true,
            isAllDay = false
        )
        assertTrue("Active timed event must be relevant",
            AwidgetProvider.Companion.isEventRelevant(currentTimed, now, today))
    }
}
