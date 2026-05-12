package com.leanbitlab.lwidget

import android.content.Context
import android.widget.RemoteViews
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.mockito.Mockito.mock
import kotlin.system.measureNanoTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class AwidgetProviderBenchmarkTest {

    @Test
    fun benchmarkFormatterCreation() {
        // Warmup
        for (i in 0 until 100) {
            DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
        }

        val iterations = 10000
        val timeNormal = measureNanoTime {
            for (i in 0 until iterations) {
                DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
            }
        }

        // Warmup Cached
        val m = AwidgetProvider.Companion::class.java.getDeclaredMethod("getFormatter", String::class.java)
        m.isAccessible = true
        for (i in 0 until 100) {
            m.invoke(AwidgetProvider.Companion, "h:mm a")
        }

        val timeCached = measureNanoTime {
            for (i in 0 until iterations) {
                m.invoke(AwidgetProvider.Companion, "h:mm a")
            }
        }

        println("BENCHMARK_NORMAL: ${timeNormal / iterations} ns per call")
        println("BENCHMARK_CACHED: ${timeCached / iterations} ns per call")
    }
}
