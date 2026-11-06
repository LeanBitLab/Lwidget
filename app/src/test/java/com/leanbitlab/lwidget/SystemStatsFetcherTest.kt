package com.leanbitlab.lwidget

import android.content.Context
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import com.leanbitlab.lwidget.stats.SystemStatsFetcher
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SystemStatsFetcherTest {

    @Test
    fun testLoadWorldClockValidZone() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val views = RemoteViews(context.packageName, R.layout.widget_layout)

        // Test 12-hour mode
        SystemStatsFetcher.loadWorldClock(views, 14f, 0xFFFFFFFF.toInt(), "UTC", is12Hour = true)

        // Test 24-hour mode
        SystemStatsFetcher.loadWorldClock(views, 14f, 0xFFFFFFFF.toInt(), "America/New_York", is12Hour = false)

        // Test invalid timezone does not crash and hides view
        SystemStatsFetcher.loadWorldClock(views, 14f, 0xFFFFFFFF.toInt(), "Invalid/Zone", is12Hour = true)
    }

    @Test
    fun testCalculateDailySteps() {
        // Normal case today
        val steps = SystemStatsFetcher.calculateDailySteps(
            totalSteps = 5500f,
            baselineSteps = 1500f,
            savedDate = "2026-09-15",
            today = "2026-09-15"
        )
        assertEquals(4000, steps)

        // Date mismatch returns 0
        val stepsOld = SystemStatsFetcher.calculateDailySteps(
            totalSteps = 5500f,
            baselineSteps = 1500f,
            savedDate = "2026-09-14",
            today = "2026-09-15"
        )
        assertEquals(0, stepsOld)
    }
}
