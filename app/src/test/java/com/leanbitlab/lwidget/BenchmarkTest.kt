package com.leanbitlab.lwidget

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.junit.Assert.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.Q])
class BenchmarkTest {

    private var lastCheckTime = 0L
    private var cachedPermission = false

    private fun checkPermissionCached(context: Context): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastCheckTime > 60000) {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            cachedPermission = (mode == AppOpsManager.MODE_ALLOWED)
            lastCheckTime = now
        }
        return cachedPermission
    }

    @Test
    fun benchmarkAppOps() {
        val context = RuntimeEnvironment.getApplication()
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val packageName = context.packageName
        val uid = Process.myUid()

        val iterations = 100000

        // Warmup IPC
        for (i in 0 until 1000) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, uid, packageName)
        }

        val startIpcTime = System.nanoTime()
        for (i in 0 until iterations) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, uid, packageName)
        }
        val endIpcTime = System.nanoTime()
        val durationIpc = (endIpcTime - startIpcTime) / 1000000
        System.err.println("Duration for $iterations checks (IPC baseline): ${durationIpc}ms")

        // Warmup cached
        for (i in 0 until 1000) {
            checkPermissionCached(context)
        }

        val startCachedTime = System.nanoTime()
        for (i in 0 until iterations) {
            checkPermissionCached(context)
        }
        val endCachedTime = System.nanoTime()
        val durationCached = (endCachedTime - startCachedTime) / 1000000
        System.err.println("Duration for $iterations checks (Cached): ${durationCached}ms")
    }
}
