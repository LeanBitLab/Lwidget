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
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

internal fun MainActivity.setupDataUsageSection() {
    // Data Usage
    val dataSwitch = bindFoldedSection(
        R.id.header_data, R.drawable.ic_data, getString(R.string.section_data_usage),
        R.id.content_data, R.id.row_data_toggle,
        "show_data_usage", false,
        sizeRowId = R.id.row_data_size, prefSizeKey = "size_data", defSize = 14f, minSize = 10f, maxSize = 74f,
        boldRowId = R.id.row_data_bold,
        isContent = true
    ).also { it.tag = "data" }
    bindToggle(R.id.row_data_bold, getString(R.string.row_bold_text), "bold_data_usage", false)

    dataSwitch.setOnCheckedChangeListener { _, isChecked ->
        if (isChecked) {
            if (!hasUsageStatsPermission()) {
                dataSwitch.isChecked = false
                try {
                    startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    com.google.android.material.snackbar.Snackbar.make(
                        findViewById(R.id.fab_update),
                        getString(R.string.perm_usage_access_title),
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                    ).show()
                } catch (e: Exception) { android.util.Log.e("MainActivity", "Exception caught", e) }
                return@setOnCheckedChangeListener
            }
            if (!checkLimit()) {
                dataSwitch.isChecked = false
                return@setOnCheckedChangeListener
            }
        }
        prefs.edit().putBoolean("show_data_usage", isChecked).apply()
        updateFeatureRowVisibility(dataSwitch, isChecked, R.id.row_data_size, R.id.row_data_bold)
        updateWidget()
        updateToggleAvailability()
        checkAllPermissions()
    }
}

internal fun MainActivity.setupRamSection() {
    // RAM
    bindFoldedSection(
        R.id.header_ram, R.drawable.ic_storage, getString(R.string.section_ram),
        R.id.content_ram, R.id.row_ram_toggle,
        "show_ram", false,
        sizeRowId = R.id.row_ram_size, prefSizeKey = "size_ram", defSize = 14f, minSize = 10f, maxSize = 74f,
        boldRowId = R.id.row_ram_bold,
        isContent = true
    ).also { it.tag = "ram" }
    bindToggle(R.id.row_ram_bold, getString(R.string.row_bold_text), "bold_ram", false)
}

internal fun MainActivity.setupStorageSection() {
    // Storage
    bindFoldedSection(
        R.id.header_storage, R.drawable.ic_storage, getString(R.string.section_storage),
        R.id.content_storage, R.id.row_storage_toggle,
        "show_storage", false,
        sizeRowId = R.id.row_storage_size, prefSizeKey = "size_storage", defSize = 14f, minSize = 10f, maxSize = 74f,
        boldRowId = R.id.row_storage_bold,
        isContent = true
    ).also { it.tag = "storage" }
    bindToggle(R.id.row_storage_bold, getString(R.string.row_bold_text), "bold_storage", false)
}

internal fun MainActivity.setupStepsSection() {
    // Steps
    val stepsSwitch = bindFoldedSection(
        R.id.header_steps, R.drawable.ic_steps, getString(R.string.section_steps),
        R.id.content_steps, R.id.row_steps_toggle,
        "show_steps", false,
        sizeRowId = R.id.row_steps_size, prefSizeKey = "size_steps", defSize = 14f, minSize = 10f, maxSize = 74f,
        boldRowId = R.id.row_steps_bold,
        isContent = true
    ).also { it.tag = "steps" }
    bindToggle(R.id.row_steps_bold, getString(R.string.row_bold_text), "bold_steps", false)

    stepsSwitch.setOnCheckedChangeListener { _, isChecked ->
        if (isChecked) {
            val hasActivityPerm = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
            if (!hasActivityPerm) {
                val neededPermissions = mutableListOf(Manifest.permission.ACTIVITY_RECOGNITION)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    neededPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                ActivityCompat.requestPermissions(this, neededPermissions.toTypedArray(), 102)
                stepsSwitch.isChecked = false
                return@setOnCheckedChangeListener
            }
            if (!checkLimit()) {
                stepsSwitch.isChecked = false
                return@setOnCheckedChangeListener
            }
        }
        prefs.edit().putBoolean("show_steps", isChecked).apply()
        val globalPrefs = getSharedPreferences("com.leanbitlab.lwidget.PREFS", Context.MODE_PRIVATE)
        if (isChecked) {
            globalPrefs.edit().putBoolean("show_steps", true).apply()
        }
        val keepAlive = prefs.getBoolean("keep_alive", false)
        val serviceIntent = Intent(this, StepCounterService::class.java)
        if (isChecked) {
            val hasPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
            } else { true }
            if (hasPermission) {
                try {
                    ContextCompat.startForegroundService(this, serviceIntent)
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to start StepCounterService", e)
                }
            } else {
                prefs.edit().putBoolean("show_steps", false).apply()
                updateWidget()
                updateToggleAvailability()
                checkAllPermissions()
                return@setOnCheckedChangeListener
            }
        } else if (!keepAlive && !StepCounterService.isStepOrKeepAliveEnabled(this)) {
            stopService(serviceIntent)
        }
        updateFeatureRowVisibility(stepsSwitch, isChecked, R.id.row_steps_size, R.id.row_steps_bold)
        updateWidget()
        updateToggleAvailability()
        checkAllPermissions()
    }
}

internal fun MainActivity.setupScreenTimeSection() {
    // Screen Time
    val screenTimeSwitch = bindFoldedSection(
        R.id.header_screen_time, R.drawable.ic_time, getString(R.string.section_screen_time),
        R.id.content_screen_time, R.id.row_screen_time_toggle,
        "show_screen_time", false,
        sizeRowId = R.id.row_screen_time_size, prefSizeKey = "size_screen_time", defSize = 14f, minSize = 10f, maxSize = 74f,
        boldRowId = R.id.row_screen_time_bold,
        isContent = true
    ).also { it.tag = "screen_time" }
    bindToggle(R.id.row_screen_time_bold, getString(R.string.row_bold_text), "bold_screen_time", false)

    screenTimeSwitch.setOnCheckedChangeListener { _, isChecked ->
        if (isChecked) {
            if (!hasUsageStatsPermission()) {
                screenTimeSwitch.isChecked = false
                try {
                    startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    com.google.android.material.snackbar.Snackbar.make(
                        findViewById(R.id.fab_update),
                        getString(R.string.perm_usage_access_title),
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                    ).show()
                } catch (e: Exception) { android.util.Log.e("MainActivity", "Failed to open Usage Access Settings", e) }
                return@setOnCheckedChangeListener
            }
            if (!checkLimit()) {
                screenTimeSwitch.isChecked = false
                return@setOnCheckedChangeListener
            }
        }
        prefs.edit().putBoolean("show_screen_time", isChecked).apply()
        updateFeatureRowVisibility(screenTimeSwitch, isChecked, R.id.row_screen_time_size, R.id.row_screen_time_bold)
        updateWidget()
        updateToggleAvailability()
        checkAllPermissions()
    }
}
