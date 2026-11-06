package com.leanbitlab.lwidget.tasks

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TasksFetcherTest {

    @Test
    fun testFormatDueSuffixZeroOrNegative() {
        assertEquals("", TasksFetcher.formatDueSuffix(0L))
        assertEquals("", TasksFetcher.formatDueSuffix(-1000L))
    }

    @Test
    fun testFormatDueSuffixOverdue() {
        val yesterday = LocalDate.now().minusDays(1)
        val millis = yesterday.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(" (Overdue)", TasksFetcher.formatDueSuffix(millis))
    }

    @Test
    fun testFormatDueSuffixToday() {
        val today = LocalDate.now()
        val millis = today.atTime(23, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(" (Today)", TasksFetcher.formatDueSuffix(millis))
    }

    @Test
    fun testFormatDueSuffixTomorrow() {
        val tomorrow = LocalDate.now().plusDays(1)
        val millis = tomorrow.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(" (Tomorrow)", TasksFetcher.formatDueSuffix(millis))
    }

    @Test
    fun testFormatDueSuffixFutureDate() {
        val future = LocalDate.now().plusDays(10)
        val millis = future.atTime(15, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val suffix = TasksFetcher.formatDueSuffix(millis)
        assert(suffix.startsWith(" (") && suffix.endsWith(")"))
    }
}
