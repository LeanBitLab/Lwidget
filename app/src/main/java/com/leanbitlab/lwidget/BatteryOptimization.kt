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

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.CompoundButton

private const val TAG = "BatteryOptimization"

internal fun Context.isIgnoringBatteryOptimizations(): Boolean =
    (getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)

/**
 * Handles a user toggle of the battery optimization switch. Turning it on asks the system to
 * exempt the app (falling back to the settings list); turning it off can only open the settings list.
 */
internal fun Context.onBatteryOptimizationToggled(isChecked: Boolean) {
    val isCurrentlyIgnoring = isIgnoringBatteryOptimizations()
    if (isChecked && !isCurrentlyIgnoring) {
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            })
        } catch (e: Exception) {
            Log.w(TAG, "Direct exemption request failed, opening settings list", e)
            openBatteryOptimizationSettings()
        }
    } else if (!isChecked && isCurrentlyIgnoring) {
        openBatteryOptimizationSettings()
    }
}

private fun Context.openBatteryOptimizationSettings() {
    try {
        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    } catch (e: Exception) {
        Log.w(TAG, "Failed to open battery optimization settings", e)
    }
}

/** Syncs the switch to the current exemption state, then routes user toggles to [onBatteryOptimizationToggled]. */
internal fun Context.bindBatteryOptimizationSwitch(switchOpt: CompoundButton) {
    switchOpt.setOnCheckedChangeListener(null)
    switchOpt.isChecked = isIgnoringBatteryOptimizations()
    switchOpt.setOnCheckedChangeListener { _, isChecked -> onBatteryOptimizationToggled(isChecked) }
}
