package com.leanbitlab.lwidget.tasks

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class TasksFetcherTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun testFormatDueSuffixZeroOrNegative() {
        assertEquals("", TasksFetcher.formatDueSuffix(context, 0L))
        assertEquals("", TasksFetcher.formatDueSuffix(context, -1000L))
    }

    @Test
    fun testFormatDueSuffixOverdue() {
        val yesterday = LocalDate.now().minusDays(1)
        val millis = yesterday.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(" (Overdue)", TasksFetcher.formatDueSuffix(context, millis))
    }

    @Test
    fun testFormatDueSuffixToday() {
        val today = LocalDate.now()
        val millis = today.atTime(23, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(" (Today)", TasksFetcher.formatDueSuffix(context, millis))
    }

    @Test
    @Config(qualifiers = "ru")
    fun testFormatDueSuffixIsLocalized() {
        val today = LocalDate.now().atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val tomorrow = LocalDate.now().plusDays(1).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(" (Сегодня)", TasksFetcher.formatDueSuffix(context, today))
        assertEquals(" (Завтра)", TasksFetcher.formatDueSuffix(context, tomorrow))
    }

    @Test
    fun testFormatDueSuffixTomorrow() {
        val tomorrow = LocalDate.now().plusDays(1)
        val millis = tomorrow.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(" (Tomorrow)", TasksFetcher.formatDueSuffix(context, millis))
    }

    @Test
    fun testFormatDueSuffixFutureDate() {
        val future = LocalDate.now().plusDays(10)
        val millis = future.atTime(15, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val suffix = TasksFetcher.formatDueSuffix(context, millis)
        assert(suffix.startsWith(" (") && suffix.endsWith(")"))
    }
}
