package com.leanbitlab.lwidget

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap

object IntentCache {
    private data class CacheEntry(val intent: Intent, val timestamp: Long)
    private val intentCache = ConcurrentHashMap<String, CacheEntry>()
    private const val CACHE_TTL_MS = 60000L // 60 seconds TTL

    fun getLaunchIntentForPackage(context: Context, packageName: String): Intent? {
        val cached = intentCache[packageName]
        val now = SystemClock.elapsedRealtime()
        if (cached != null && (now - cached.timestamp < CACHE_TTL_MS)) {
            return Intent(cached.intent)
        }

        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intentCache[packageName] = CacheEntry(Intent(intent), now)
            return Intent(intent)
        }
        return null
    }

    fun getBestIntent(context: Context, packages: List<String>, fallback: Intent): Intent {
        val cacheKey = packages.joinToString(",") + "|" + fallback.action
        val cached = intentCache[cacheKey]
        val now = SystemClock.elapsedRealtime()
        if (cached != null && (now - cached.timestamp < CACHE_TTL_MS)) {
            return Intent(cached.intent)
        }

        val pm = context.packageManager
        for (pkg in packages) {
            val intent = pm.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intentCache[cacheKey] = CacheEntry(Intent(intent), now)
                return Intent(intent)
            }
        }
        intentCache[cacheKey] = CacheEntry(Intent(fallback), now)
        return fallback
    }
}
