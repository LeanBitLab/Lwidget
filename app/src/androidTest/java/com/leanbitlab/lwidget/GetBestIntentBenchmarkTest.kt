package com.leanbitlab.lwidget

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureNanoTime

@RunWith(AndroidJUnit4::class)
class GetBestIntentBenchmarkTest {

    @Test
    fun benchmarkGetBestIntent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clockPackages = listOf("com.android.deskclock", "com.google.android.deskclock", "com.simplemobiletools.clock", "org.fossify.clock", "com.sec.android.app.clockpackage", "com.coloros.alarmclock", "com.miui.calculator")
        val fallback = Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)

        // Warmup
        for (i in 0 until 10) {
            getBestIntent(context, clockPackages, fallback)
        }

        val iterations = 100
        val timeNormal = measureNanoTime {
            for (i in 0 until iterations) {
                getBestIntent(context, clockPackages, fallback)
            }
        }

        // Warmup Cached
        for (i in 0 until 10) {
            getBestIntentCached(context, clockPackages, fallback)
        }

        val timeCached = measureNanoTime {
            for (i in 0 until iterations) {
                getBestIntentCached(context, clockPackages, fallback)
            }
        }

        println("BENCHMARK_NORMAL: ${timeNormal / iterations} ns per call")
        println("BENCHMARK_CACHED: ${timeCached / iterations} ns per call")
    }

    private fun getBestIntent(context: Context, packages: List<String>, fallback: Intent): Intent {
        val pm = context.packageManager
        for (pkg in packages) {
            val intent = pm.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                return intent
            }
        }
        return fallback
    }

    private var cachedClockIntent: Intent? = null

    private fun getBestIntentCached(context: Context, packages: List<String>, fallback: Intent): Intent {
        if (cachedClockIntent != null) return cachedClockIntent!!
        val pm = context.packageManager
        for (pkg in packages) {
            val intent = pm.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                cachedClockIntent = intent
                return intent
            }
        }
        cachedClockIntent = fallback
        return fallback
    }
}
