package com.leanbitlab.lwidget

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class GetLaunchIntentBenchmarkTest {

    @Test
    fun benchmarkGetLaunchIntent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val shadowPackageManager = shadowOf(context.packageManager)

        // Mock a launch intent for a package
        val mockIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        shadowPackageManager.addActivityIfNotPresent(
            android.content.ComponentName("org.tasks", "org.tasks.MainActivity")
        )
        // Note: Robolectric's implementation of getLaunchIntentForPackage might not perfectly
        // reflect real device performance, but we can see relative difference if we implement caching.

        val start = System.currentTimeMillis()
        for (i in 1..1000) {
            context.packageManager.getLaunchIntentForPackage("org.tasks")
        }
        val end = System.currentTimeMillis()
        println("1000 calls to getLaunchIntentForPackage took ${end - start} ms")
    }
}
