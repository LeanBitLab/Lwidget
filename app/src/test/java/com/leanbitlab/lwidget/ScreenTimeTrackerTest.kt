package com.leanbitlab.lwidget

import com.leanbitlab.lwidget.stats.ScreenTimeTracker
import com.leanbitlab.lwidget.stats.ScreenTimeTracker.Event
import com.leanbitlab.lwidget.stats.ScreenTimeTracker.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Screen time is summed from exact foreground intervals, excluding the launcher. */
class ScreenTimeTrackerTest {

    private val resumed = 1
    private val paused = 2
    private val screenOff = 16
    private val start = State("2026-11-20", 0L, 0L)

    @Test
    fun sumsIntervalsAndSkipsLauncher() {
        val s = ScreenTimeTracker.fold(start, listOf(
            Event(resumed, "a", 1_000), Event(paused, "a", 4_000),
            Event(resumed, "home", 4_000), Event(paused, "home", 9_000),
            Event(resumed, "b", 9_000), Event(paused, "b", 10_000)
        ), excludedPkg = "home")
        assertEquals(4_000, s.totalMs)
        assertNull(s.openPkg)
    }

    @Test
    fun openAppCountsUntilNowAndAcrossCalls() {
        val first = ScreenTimeTracker.fold(start, listOf(Event(resumed, "a", 1_000)), null)
        assertEquals(2_000, first.totalAt(3_000))
        // Next tick sees only new events; the open interval continues
        val second = ScreenTimeTracker.fold(first, listOf(Event(paused, "a", 5_000)), null)
        assertEquals(4_000, second.totalMs)
    }

    @Test
    fun screenOffClosesInterval() {
        val s = ScreenTimeTracker.fold(start, listOf(
            Event(resumed, "a", 1_000), Event(screenOff, "android", 2_000), Event(paused, "a", 60_000)
        ), null)
        assertEquals(1_000, s.totalMs)
    }

    @Test
    fun switchingAppsDoesNotDoubleCount() {
        // Next app resumes before the previous one reports paused
        val s = ScreenTimeTracker.fold(start, listOf(
            Event(resumed, "a", 0), Event(resumed, "b", 3_000), Event(paused, "a", 3_100), Event(paused, "b", 5_000)
        ), null)
        assertEquals(5_000, s.totalMs)
    }
}
