/*
 * Copyright (C) 2026 LeanBitLab
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */


package com.leanbitlab.lwidget

import android.Manifest
import android.util.Log
import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import android.content.pm.PackageManager
import android.view.View
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.switchmaterial.SwitchMaterial

internal fun MainActivity.checkAllPermissions() {
    var widgetNeedsUpdate = false

    // Check Calendar
    val calMissing = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED
    if (prefs.getBoolean("show_events", false) && calMissing) {
        prefs.edit().putBoolean("show_events", false).apply()
        findViewById<View>(R.id.row_events_toggle).findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.row_switch).isChecked = false
        findViewById<View>(R.id.row_events_size).visibility = View.GONE
        widgetNeedsUpdate = true
    }

    // Check Tasks
    val tasksMissing = ContextCompat.checkSelfPermission(this, AwidgetProvider.PERMISSION_READ_TASKS_ORG) != PackageManager.PERMISSION_GRANTED
    if (prefs.getBoolean("show_tasks", false) && tasksMissing) {
         prefs.edit().putBoolean("show_tasks", false).apply()
         findViewById<View>(R.id.row_tasks_toggle).findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.row_switch).isChecked = false
         findViewById<View>(R.id.row_tasks_size).visibility = View.GONE
         widgetNeedsUpdate = true
    }

    // Check Steps
    var stepMissing = false
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
        stepMissing = true
    }
    if (prefs.getBoolean("show_steps", false) && stepMissing) {
         prefs.edit().putBoolean("show_steps", false).apply()
         findViewById<View>(R.id.row_steps_toggle).findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.row_switch).isChecked = false
         findViewById<View>(R.id.row_steps_size).visibility = View.GONE
         widgetNeedsUpdate = true
    }

    // Check Screen Time
    val usageMissing = !hasUsageStatsPermission()
    if (prefs.getBoolean("show_screen_time", false) && usageMissing) {
        prefs.edit().putBoolean("show_screen_time", false).apply()
        findViewById<View>(R.id.row_screen_time_toggle).findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.row_switch).isChecked = false
        findViewById<View>(R.id.row_screen_time_size).visibility = View.GONE
        widgetNeedsUpdate = true
    }

    // Check Data Usage
    if (prefs.getBoolean("show_data_usage", false) && usageMissing) {
        prefs.edit().putBoolean("show_data_usage", false).apply()
        findViewById<View>(R.id.row_data_toggle).findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.row_switch).isChecked = false
        findViewById<View>(R.id.row_data_size).visibility = View.GONE
        widgetNeedsUpdate = true
    }

    // Check Breezy Weather
    val weatherMissing = !isAppInstalled("org.breezyweather") || ContextCompat.checkSelfPermission(this, "org.breezyweather.READ_PROVIDER") != PackageManager.PERMISSION_GRANTED
    if (prefs.getBoolean("show_weather_condition", false) && weatherMissing) {
        prefs.edit().putBoolean("show_weather_condition", false).apply()
        findViewById<View>(R.id.row_weather_toggle).findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.row_switch).isChecked = false
        findViewById<View>(R.id.row_weather_size).visibility = View.GONE
        widgetNeedsUpdate = true
    }

    if (widgetNeedsUpdate) {
        updateWidget()
        updateToggleAvailability()
    }
    
    // Update Permission Toggles
    updatePermissionToggle(R.id.row_perm_calendar, getString(R.string.section_events), !calMissing) {
        if (calMissing) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CALENDAR), 100)
        else openAppSettings()
    }
    updatePermissionToggle(R.id.row_perm_tasks, getString(R.string.section_tasks), !tasksMissing) {
        if (tasksMissing) ActivityCompat.requestPermissions(this, arrayOf(AwidgetProvider.PERMISSION_READ_TASKS_ORG), 101)
        else openAppSettings()
    }
    updatePermissionToggle(R.id.row_perm_steps, getString(R.string.section_steps), !stepMissing) {
        if (stepMissing) {
            val neededPermissions = mutableListOf<String>()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                neededPermissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                neededPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            ActivityCompat.requestPermissions(this, neededPermissions.toTypedArray(), 102)
        } else openAppSettings()
    }
    updatePermissionToggle(R.id.row_perm_data_usage, getString(R.string.section_data_usage), !usageMissing) {
        openUsageAccessSettings()
    }
    updatePermissionToggle(R.id.row_perm_screen_time, getString(R.string.section_screen_time), !usageMissing) {
        openUsageAccessSettings()
    }
    updatePermissionToggle(R.id.row_perm_weather, getString(R.string.section_weather_condition), !weatherMissing) {
        if (weatherMissing && !isAppInstalled("org.breezyweather")) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=org.breezyweather")))
            } catch (e: Exception) { Log.w("MainActivity", "Failed to open market link for BreezyWeather", e)
                try {
                    CustomTabsIntent.Builder().build().launchUrl(this@checkAllPermissions, android.net.Uri.parse("https://f-droid.org/packages/org.breezyweather/"))
                } catch (e2: Exception) { Log.w("MainActivity", "Failed to launch custom tab for BreezyWeather", e2) }
            }
        } else if (weatherMissing) {
            ActivityCompat.requestPermissions(this, arrayOf("org.breezyweather.READ_PROVIDER"), 104)
        } else openAppSettings()
    }
}

internal fun MainActivity.updatePermissionToggle(viewId: Int, label: String, isGranted: Boolean, onClick: () -> Unit) {
    val row = findViewById<View>(viewId)
    if (row != null) {
        row.findViewById<TextView>(R.id.row_label).text = label
        val switchView = row.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.row_switch)
        switchView.setOnCheckedChangeListener(null)
        switchView.isChecked = isGranted
        
        row.setOnClickListener { onClick() }
        switchView.setOnClickListener {
            switchView.isChecked = isGranted // Revert instantly
            onClick()
        }
    }
}

internal fun MainActivity.openAppSettings() {
    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
    intent.data = android.net.Uri.parse("package:" + packageName)
    startActivity(intent)
}

// Check usage stats permission
internal fun MainActivity.hasUsageStatsPermission(): Boolean {
    val appOps = getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
    val opMode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, 
            android.os.Process.myUid(), packageName)
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, 
            android.os.Process.myUid(), packageName)
    }
    return opMode == android.app.AppOpsManager.MODE_ALLOWED
}

internal fun MainActivity.isAppInstalled(packageName: String): Boolean {
    return try {
        packageManager.getPackageInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}

internal fun MainActivity.setupKeepAliveSection() {
    // Keep Alive (now inside Advanced section, not folded)
    bindToggle(R.id.row_keep_alive_toggle, getString(R.string.row_enable_keep_alive), "keep_alive", false) { isChecked ->
        prefs.edit().putBoolean("keep_alive", isChecked).apply()
        getSharedPreferences("com.leanbitlab.lwidget.PREFS", Context.MODE_PRIVATE)
            .edit().putBoolean("keep_alive", isChecked).apply()

        if (isChecked) {
            val missing = StepCounterService.missingKeepAlivePermissions(this)
            if (missing.isNotEmpty()) {
                // Service starts (or the switch reverts) in onRequestPermissionsResult
                ActivityCompat.requestPermissions(this, missing, REQUEST_KEEP_ALIVE_PERMISSIONS)
            } else {
                StepCounterService.startServiceIfEnabled(this)
            }
        } else {
            if (!StepCounterService.isStepOrKeepAliveEnabled(this)) {
                stopService(Intent(this, StepCounterService::class.java))
            } else {
                StepCounterService.startServiceIfEnabled(this)
            }
        }
    }
}

internal const val REQUEST_KEEP_ALIVE_PERMISSIONS = 105

/** After the keep-alive permission prompt: start the service, or turn keep-alive off if it cannot run. */
internal fun MainActivity.onKeepAlivePermissionsResult() {
    if (StepCounterService.canStart(this)) {
        StepCounterService.startServiceIfEnabled(this)
        return
    }
    prefs.edit().putBoolean("keep_alive", false).apply()
    getSharedPreferences("com.leanbitlab.lwidget.PREFS", Context.MODE_PRIVATE)
        .edit().putBoolean("keep_alive", false).apply()
    findViewById<View>(R.id.row_keep_alive_toggle)
        ?.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.row_switch)?.isChecked = false
    android.widget.Toast.makeText(this, R.string.msg_keep_alive_needs_activity, android.widget.Toast.LENGTH_LONG).show()
}

internal fun MainActivity.setupBatteryOptimizationSection() {
    val row = findViewById<View>(R.id.row_battery_optimization_toggle)
    val tvTitle = row.findViewById<TextView>(R.id.row_label)
    tvTitle.text = getString(R.string.row_battery_optimization)
    updateBatteryOptimizationSwitch()
}

internal fun MainActivity.updateBatteryOptimizationSwitch() {
    val row = findViewById<View>(R.id.row_battery_optimization_toggle) ?: return
    val switchOpt = row.findViewById<SwitchMaterial>(R.id.row_switch) ?: return
    bindBatteryOptimizationSwitch(switchOpt)
}

private fun MainActivity.openUsageAccessSettings() {
    try {
        startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
    } catch (e: Exception) {
        Log.w("MainActivity", "Failed to launch usage access settings", e)
    }
}
