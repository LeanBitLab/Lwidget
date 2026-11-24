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
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import android.content.pm.PackageManager
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

internal fun MainActivity.getInstalledCalendarApps(): Pair<List<String>, List<String>> {
    val labels = mutableListOf(getString(R.string.font_default))
    val packages = mutableListOf("default")

    val knownPackages = listOf(
        "org.fossify.calendar",
        "com.simplemobiletools.calendar",
        "com.google.android.calendar",
        "com.android.calendar",
        "com.samsung.android.calendar",
        "com.bbk.calendar",
        "com.miui.calendar",
        "com.huawei.calendar"
    )

    val pm = packageManager
    val calIntent = Intent(Intent.ACTION_VIEW).apply {
        data = android.net.Uri.parse("content://com.android.calendar/time")
    }
    val resolveInfos = pm.queryIntentActivities(calIntent, PackageManager.MATCH_DEFAULT_ONLY)

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

internal fun MainActivity.setupEventsAndTasksSections() {
    val (calLabels, calPackages) = getInstalledCalendarApps()
    calendarAppPackages = calPackages

    val calAppRow = findViewById<View>(R.id.row_events_calendar_app)

    // Events
    val eventsSwitch = bindFoldedSection(
        R.id.header_events, R.drawable.ic_events, getString(R.string.section_events),
        R.id.content_events, R.id.row_events_toggle,
        "show_events", false,
        sizeRowId = R.id.row_events_size, prefSizeKey = "size_events", defSize = 14f, minSize = 10f, maxSize = 18f,
        isContent = true,
        onChanged = { isShown ->
            calAppRow?.visibility = if (isShown) View.VISIBLE else View.GONE
        }
    )

    bindSelector(
        R.id.row_events_calendar_app,
        getString(R.string.section_events_calendar_app),
        "calendar_app_package",
        calLabels,
        0
    )

    // Show day abbreviation in events (from issue #71)
    bindToggle(R.id.row_events_day_abbr, getString(R.string.row_show_day_abbr), "show_day_abbr_in_events", true)

    // Tasks
    val tasksSwitch = bindFoldedSection(
        R.id.header_tasks, R.drawable.ic_tasks, getString(R.string.section_tasks),
        R.id.content_tasks, R.id.row_tasks_toggle,
        "show_tasks", false,
        sizeRowId = R.id.row_tasks_size, prefSizeKey = "size_tasks", defSize = 14f, minSize = 10f, maxSize = 18f,
        isContent = true
    )

    // Events and Tasks can both be on: the widget then shows a small switch to flip between them
    eventsSwitch.setOnCheckedChangeListener { _, isChecked ->
        if (isChecked) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CALENDAR), 100)
                eventsSwitch.isChecked = false
                return@setOnCheckedChangeListener
            }
            if (checkLimit()) {
                prefs.edit().putBoolean("show_events", true).apply()
                updateFeatureRowVisibility(eventsSwitch, true, R.id.row_events_size, R.id.row_events_day_abbr, R.id.row_events_calendar_app)
                updateWidget()
                updateToggleAvailability()
                checkAllPermissions()
            } else { eventsSwitch.isChecked = false }
        } else {
            prefs.edit().putBoolean("show_events", false).apply()
            updateFeatureRowVisibility(eventsSwitch, false, R.id.row_events_size, R.id.row_events_day_abbr, R.id.row_events_calendar_app)
            updateWidget()
            updateToggleAvailability()
            checkAllPermissions()
        }
    }
    tasksSwitch.setOnCheckedChangeListener { _, isChecked ->
        if (isChecked) {
            if (!isAppInstalled("org.tasks")) {
                tasksSwitch.isChecked = false
                com.google.android.material.snackbar.Snackbar.make(
                    findViewById(R.id.fab_update),
                    getString(R.string.msg_tasks_required),
                    com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                ).setAction(getString(R.string.action_install)) {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=org.tasks")))
                    } catch (e: Exception) {
                        CustomTabsIntent.Builder().build().launchUrl(this@setupEventsAndTasksSections, android.net.Uri.parse("https://play.google.com/store/apps/details?id=org.tasks"))
                    }
                }.show()
                return@setOnCheckedChangeListener
            }
            if (ContextCompat.checkSelfPermission(this, AwidgetProvider.PERMISSION_READ_TASKS_ORG) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(AwidgetProvider.PERMISSION_READ_TASKS_ORG), 101)
            }
            if (checkLimit()) {
                prefs.edit().putBoolean("show_tasks", true).apply()
                updateFeatureRowVisibility(tasksSwitch, true, R.id.row_tasks_size)
                updateWidget()
                updateToggleAvailability()
                checkAllPermissions()
            } else { tasksSwitch.isChecked = false }
        } else {
            prefs.edit().putBoolean("show_tasks", false).apply()
            updateFeatureRowVisibility(tasksSwitch, false, R.id.row_tasks_size)
            updateWidget()
            updateToggleAvailability()
            checkAllPermissions()
        }
    }
}
