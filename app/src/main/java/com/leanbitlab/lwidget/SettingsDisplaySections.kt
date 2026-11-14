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

import android.util.Log
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import android.content.pm.PackageManager
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

internal fun MainActivity.getInstalledClockApps(): Pair<List<String>, List<String>> {
    val labels = mutableListOf("Default")
    val packages = mutableListOf("default")

    val knownPackages = listOf(
        "com.android.deskclock",
        "com.google.android.deskclock",
        "com.simplemobiletools.clock",
        "org.fossify.clock",
        "com.sec.android.app.clockpackage",
        "com.huawei.deskclock",
        "com.coloros.alarmclock",
        "com.oneplus.deskclock",
        "com.htc.sec.android.app.clockpackage"
    )

    val pm = packageManager
    
    val alarmIntent = Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)
    val resolveInfos = pm.queryIntentActivities(alarmIntent, PackageManager.MATCH_DEFAULT_ONLY)
    
    val foundPackages = mutableSetOf<String>()
    for (info in resolveInfos) {
        val pkg = info.activityInfo.packageName
        foundPackages.add(pkg)
    }
    
    for (pkg in knownPackages) {
        if (isAppInstalled(pkg)) {
            foundPackages.add(pkg)
        }
    }
    
    for (pkg in foundPackages) {
        try {
            val appInfo = pm.getApplicationInfo(pkg, 0)
            val label = pm.getApplicationLabel(appInfo).toString()
            labels.add(label)
            packages.add(pkg)
        } catch (e: Exception) {
            // ignore
        }
    }
    
    return Pair(labels, packages)
}

internal fun MainActivity.setupTimeSection(timeFormatOptions: List<String>) {
    val (labels, packages) = getInstalledClockApps()
    clockAppPackages = packages

    val clockAppRow = findViewById<View>(R.id.row_time_clock_app)

    // Time
    bindFoldedSection(
        R.id.header_time, R.drawable.ic_time, getString(R.string.section_time),
        R.id.content_time, R.id.row_time_toggle,
        "show_time", true,
        sizeRowId = R.id.row_time_size, prefSizeKey = "size_time", defSize = 56f, minSize = 12f, maxSize = 120f,
        selectorRowId = R.id.row_time_format, selectorOptions = timeFormatOptions, prefSelectorKey = "time_format_idx", defSelectorIdx = 0,
        isContent = true,
        onChanged = { isShown ->
            clockAppRow?.visibility = if (isShown) View.VISIBLE else View.GONE
        }
    )

    bindSelector(
        R.id.row_time_clock_app,
        getString(R.string.section_time_clock_app),
        "clock_app_package",
        labels,
        0
    )
}

internal fun MainActivity.setupNextAlarmSection() {
    // Next Alarm
    bindFoldedSection(
        R.id.header_next_alarm, R.drawable.ic_alarm, getString(R.string.section_next_alarm),
        R.id.content_next_alarm, R.id.row_next_alarm_toggle,
        "show_next_alarm", true,
        sizeRowId = R.id.row_next_alarm_size, prefSizeKey = "size_next_alarm", defSize = 14f, minSize = 10f, maxSize = 24f,
        isContent = true
    )
}

internal fun MainActivity.setupWorldClockSection(zoneIds: List<String>) {
    // World Clock
    bindFoldedSection(
        R.id.header_world_clock, R.drawable.ic_world, getString(R.string.section_world_clock),
        R.id.content_world_clock, R.id.row_world_clock_toggle,
        "show_world_clock", false,
        sizeRowId = R.id.row_world_clock_size, prefSizeKey = "size_world_clock", defSize = 18f, minSize = 10f, maxSize = 32f,
        isContent = true
    )
    bindTimezoneSearch(R.id.row_world_clock_zone, zoneIds, "world_clock_zone_str", "UTC")
}

internal fun MainActivity.setupDateSection(dateFormatOptions: List<String>) {
    // Date
    bindFoldedSection(
        R.id.header_date, R.drawable.ic_date, getString(R.string.section_date),
        R.id.content_date, R.id.row_date_toggle,
        "show_date", true,
        sizeRowId = R.id.row_date_size, prefSizeKey = "size_date", defSize = 16f, minSize = 10f, maxSize = 24f,
        selectorRowId = R.id.row_date_format, selectorOptions = dateFormatOptions, prefSelectorKey = "date_format_idx", defSelectorIdx = 0,
        isContent = true
    )
}

internal fun MainActivity.setupBatterySection() {
    // Battery
    bindFoldedSection(
        R.id.header_battery, R.drawable.ic_battery, getString(R.string.section_battery),
        R.id.content_battery, R.id.row_battery_toggle,
        "show_battery", true,
        sizeRowId = R.id.row_battery_size, prefSizeKey = "size_battery", defSize = 32f, minSize = 10f, maxSize = 74f,
        boldRowId = R.id.row_battery_bold,
        isContent = true
    ).also { it.tag = "battery" }
    bindToggle(R.id.row_battery_bold, getString(R.string.row_bold_text), "bold_battery", true)
}

internal fun MainActivity.setupTempSection() {
    // Temp
    val tempUnitOptions = listOf("Celsius (°C)", "Fahrenheit (°F)")
    bindFoldedSection(
        R.id.header_temp, R.drawable.ic_temp, getString(R.string.section_temp),
        R.id.content_temp, R.id.row_temp_toggle,
        "show_temp", false,
        sizeRowId = R.id.row_temp_size, prefSizeKey = "size_temp", defSize = 18f, minSize = 10f, maxSize = 74f,
        selectorRowId = R.id.row_temp_unit, selectorOptions = tempUnitOptions, prefSelectorKey = "temp_unit_idx", defSelectorIdx = 0,
        boldRowId = R.id.row_temp_bold,
        isContent = true
    ).also { it.tag = "temp" }
    bindToggle(R.id.row_temp_bold, getString(R.string.row_bold_text), "bold_temp", false)
}

internal fun MainActivity.setupWeatherSection() {
    // Weather
    val weatherSwitch = bindFoldedSection(
        R.id.header_weather, R.drawable.ic_weather, getString(R.string.section_weather_condition),
        R.id.content_weather, R.id.row_weather_toggle,
        "show_weather_condition", false,
        sizeRowId = R.id.row_weather_size, prefSizeKey = "size_weather", defSize = 18f, minSize = 10f, maxSize = 74f,
        boldRowId = R.id.row_weather_bold,
        isContent = true
    ).also { it.tag = "weather_condition" }
    bindToggle(R.id.row_weather_bold, getString(R.string.row_bold_text), "bold_weather", false)

    // Override weather listener for Breezy Weather check
    weatherSwitch.setOnCheckedChangeListener { _, isChecked ->
        if (isChecked) {
            if (!isAppInstalled("org.breezyweather")) {
                weatherSwitch.isChecked = false
                com.google.android.material.snackbar.Snackbar.make(
                    findViewById(R.id.fab_update),
                    "Breezy Weather app (with DataBridge enabled) is required.",
                    com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                ).setAction("Install") {
                    try {
                        CustomTabsIntent.Builder().build().launchUrl(this@setupWeatherSection, android.net.Uri.parse("https://github.com/breezy-weather/breezy-weather/releases"))
                    } catch (e: Exception) { android.util.Log.e("MainActivity", "Exception caught", e) }
                }.show()
                return@setOnCheckedChangeListener
            }
            if (ContextCompat.checkSelfPermission(this, "org.breezyweather.READ_PROVIDER") != PackageManager.PERMISSION_GRANTED) {
                weatherSwitch.isChecked = false
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("Permission Clarification")
                    .setMessage("To display the weather, Lwidget needs to read data from Breezy Weather.\n\nAndroid will now ask for 'Location' access. Please note: Lwidget DOES NOT access your location, nor does it have permission to access the internet. This is simply how Android categorizes Breezy Weather's data sharing permission.")
                    .setPositiveButton("Continue") { _, _ ->
                        ActivityCompat.requestPermissions(this, arrayOf("org.breezyweather.READ_PROVIDER"), 103)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                return@setOnCheckedChangeListener
            }
            if (!checkLimit()) {
                weatherSwitch.isChecked = false
                return@setOnCheckedChangeListener
            }
        }
        prefs.edit().putBoolean("show_weather_condition", isChecked).apply()
        updateFeatureRowVisibility(weatherSwitch, isChecked, R.id.row_weather_size, R.id.row_weather_bold)
        updateWidget()
        updateToggleAvailability()
        if (isChecked) {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Important Step")
                .setMessage("If the weather doesn't show up on your widget soon:\n\nOpen Breezy Weather → Settings → External Modules → Enable 'Send Gadgetbridge Data' & toggle on 'Lwidget'.")
                .setPositiveButton("Got it", null)
                .show()
        }
    }
}
