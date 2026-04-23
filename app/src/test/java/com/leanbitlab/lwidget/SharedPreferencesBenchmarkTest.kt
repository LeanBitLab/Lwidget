package com.leanbitlab.lwidget

import android.content.Context
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import android.os.Build

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.Q])
class SharedPreferencesBenchmarkTest {

    @Test
    fun benchmarkPrefs() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("com.leanbitlab.lwidget.PREFS", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("usage_stats_permission_granted", true).commit()

        val iterations = 100000

        // Warmup
        for (i in 0 until 1000) {
            prefs.getBoolean("usage_stats_permission_granted", false)
        }

        val startTime = System.nanoTime()
        for (i in 0 until iterations) {
            prefs.getBoolean("usage_stats_permission_granted", false)
        }
        val endTime = System.nanoTime()

        System.err.println("Duration for $iterations shared preferences reads: ${(endTime - startTime) / 1000000}ms")
    }
}
