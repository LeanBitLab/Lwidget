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
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import android.view.ViewGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private val contentSwitches = mutableListOf<SwitchMaterial>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("com.leanbitlab.lwidget.PREFS", Context.MODE_PRIVATE)

        checkAllPermissions()
        setupSections()
        
        // Setup Changelog
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            "Unknown"
        }
        val tvVersion = findViewById<TextView>(R.id.tv_changelog_version)
        tvVersion.text = getString(R.string.changelog_version, versionName)

        val cardChangelog = findViewById<MaterialCardView>(R.id.card_changelog)
        val changelogContent = findViewById<View>(R.id.changelog_expandable_content)
        val ivChangelogExpand = findViewById<android.widget.ImageView>(R.id.iv_changelog_expand)
        cardChangelog.setOnClickListener {
            val isCurrentlyVisible = changelogContent.visibility == View.VISIBLE
            changelogContent.visibility = if (isCurrentlyVisible) View.GONE else View.VISIBLE
            ivChangelogExpand.animate().rotation(if (isCurrentlyVisible) 0f else 180f).setDuration(200).start()
        }

        // Prevent parent scroll when touching the inner changelog scroll area
        findViewById<View>(R.id.changelog_scroll).setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_MOVE ->
                    v.parent.requestDisallowInterceptTouchEvent(true)
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL ->
                    v.parent.requestDisallowInterceptTouchEvent(false)
            }
            false
        }

        findViewById<View>(R.id.tv_github_link).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/LeanBitLab/Lwidget"))
            startActivity(intent)
        }

        findViewById<View>(R.id.tv_privacy_policy).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/LeanBitLab/Lwidget/wiki/Privacy-Policy"))
            startActivity(intent)
        }

        
        val fab = findViewById<ExtendedFloatingActionButton>(R.id.fab_update)
        fab.setOnClickListener {
            updateWidget()
        }

        // Apply navigation bar insets to FAB so it doesn't overlap gesture nav
        ViewCompat.setOnApplyWindowInsetsListener(fab) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = insets.bottom + (24 * resources.displayMetrics.density).toInt()
                rightMargin = insets.right + (24 * resources.displayMetrics.density).toInt()
            }
            windowInsets
        }

        // Handle Collapsing Toolbar Title Fade and Header Fade
        val appBar = findViewById<com.google.android.material.appbar.AppBarLayout>(R.id.app_bar)
        val titleApp = findViewById<TextView>(R.id.title_app)
        val expandedHeader = findViewById<View>(R.id.header_expanded)
        
        appBar.addOnOffsetChangedListener(com.google.android.material.appbar.AppBarLayout.OnOffsetChangedListener { _, verticalOffset ->
            val totalScrollRange = appBar.totalScrollRange
            val percentage = kotlin.math.abs(verticalOffset).toFloat() / totalScrollRange.toFloat()
            
            // Fade in toolbar title when nearing collapse (e.g. last 20% of scroll)
            val alphaTitle = ((percentage - 0.8f) / 0.2f).coerceIn(0f, 1f)
            titleApp.alpha = alphaTitle

            // Fade out expanded header as we scroll up (first 50% of scroll)
            // Starts dense (1f) and fades to 0f by the time we are halfway collapsed
            val alphaHeader = (1f - (percentage / 0.5f)).coerceIn(0f, 1f)
            expandedHeader.alpha = alphaHeader
            // Optional: Scale down slightly for a nicer effect
            val scale = (1f - (percentage * 0.1f)).coerceIn(0.9f, 1f)
            expandedHeader.scaleX = scale
            expandedHeader.scaleY = scale
        })
    }

    private fun checkAllPermissions() {
        val cardPermissionList = findViewById<View>(R.id.card_permission_list)
        var widgetNeedsUpdate = false

        // Check Calendar
        if (prefs.getBoolean("show_events", false) && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            prefs.edit().putBoolean("show_events", false).apply()
            findViewById<View>(R.id.section_events).findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.item_switch).isChecked = false
            findViewById<View>(R.id.section_events).findViewById<View>(R.id.size_container).visibility = View.GONE
            widgetNeedsUpdate = true
        }

        // Check Tasks
        if (prefs.getBoolean("show_tasks", false) && ContextCompat.checkSelfPermission(this, "org.tasks.permission.READ_TASKS") != PackageManager.PERMISSION_GRANTED) {
             prefs.edit().putBoolean("show_tasks", false).apply()
             findViewById<View>(R.id.section_tasks).findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.item_switch).isChecked = false
             findViewById<View>(R.id.section_tasks).findViewById<View>(R.id.size_container).visibility = View.GONE
             widgetNeedsUpdate = true
        }

        // Check Steps
        var stepMissing = false
        if (prefs.getBoolean("show_steps", false)) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                stepMissing = true
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                stepMissing = true
            }
        }
        if (stepMissing) {
             prefs.edit().putBoolean("show_steps", false).apply()
             findViewById<View>(R.id.section_steps).findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.item_switch).isChecked = false
             findViewById<View>(R.id.section_steps).findViewById<View>(R.id.size_container).visibility = View.GONE
             widgetNeedsUpdate = true
        }

        // Check Screen Time
        if (prefs.getBoolean("show_screen_time", false) && !hasUsageStatsPermission()) {
            prefs.edit().putBoolean("show_screen_time", false).apply()
            findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.section_screen_time).isChecked = false
            findViewById<View>(R.id.section_screen_time).findViewById<View>(R.id.size_container).visibility = View.GONE
            widgetNeedsUpdate = true
        }

        // Check Data Usage
        if (prefs.getBoolean("show_data_usage", false) && !hasUsageStatsPermission()) {
            prefs.edit().putBoolean("show_data_usage", false).apply()
            findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.section_data).isChecked = false
            findViewById<View>(R.id.section_data).findViewById<View>(R.id.size_container).visibility = View.GONE
            widgetNeedsUpdate = true
        }

        cardPermissionList.visibility = View.GONE

        if (widgetNeedsUpdate) {
            updateWidget()
            updateToggleAvailability()
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check permissions when returning (especially for Data Usage settings)
        checkAllPermissions()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        checkAllPermissions()
        if (requestCode == 101) {
             // Task permission result - trigger update
             if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                 updateWidget()
             }
        }
    }

    private fun setupSections() {
        contentSwitches.clear()

        // Time: Def True, 64sp
        bindSection(R.id.section_time, getString(R.string.section_time), "show_time", true, "size_time", 64f, 12f, 120f, isContent = true, iconResId = R.drawable.ic_time) { isChecked ->
             findViewById<View>(R.id.section_time_format).visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        bindSelector(R.id.section_time_format, getString(R.string.section_time_format), "time_format_idx", listOf(getString(R.string.format_12h), getString(R.string.format_24h)), 0, iconResId = R.drawable.ic_time) 
        
        // World Clock: Def False, 18sp (New)
        bindSection(R.id.section_world_clock, getString(R.string.section_world_clock), "show_world_clock", false, "size_world_clock", 18f, 10f, 32f, isContent = true, iconResId = R.drawable.ic_world) { isChecked ->
             findViewById<View>(R.id.section_world_clock_zone).visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        
        val zoneIds = java.time.ZoneId.getAvailableZoneIds().sorted()
        bindSelector(R.id.section_world_clock_zone, "Timezone", "world_clock_zone_str", zoneIds, zoneIds.indexOf("UTC").takeIf { it >= 0 } ?: 0, iconResId = R.drawable.ic_world)
        
        // Next Alarm: Def True, 14sp (New) - Moved up
        bindSection(R.id.section_next_alarm, getString(R.string.section_next_alarm), "show_next_alarm", true, "size_next_alarm", 14f, 10f, 24f, isContent = true, iconResId = R.drawable.ic_alarm)

        // Date: Def True, 14sp
        bindSection(R.id.section_date, getString(R.string.section_date), "show_date", true, "size_date", 14f, 10f, 24f, isContent = true, iconResId = R.drawable.ic_date) { isChecked ->
             findViewById<View>(R.id.section_date_format).visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        bindSelector(R.id.section_date_format, getString(R.string.section_date_format), "date_format_idx", listOf(getString(R.string.date_format_full), getString(R.string.date_format_short), getString(R.string.date_format_numeric)), 0, iconResId = R.drawable.ic_date)
        
        // Battery: Def True, 24sp
        bindSection(R.id.section_battery, getString(R.string.section_battery), "show_battery", true, "size_battery", 24f, 12f, 74f, isContent = true, iconResId = R.drawable.ic_battery).tag = "battery"
        
        // Temp: Def True, 18sp
        bindSection(R.id.section_temp, getString(R.string.section_temp), "show_temp", true, "size_temp", 18f, 10f, 32f, isContent = true, iconResId = R.drawable.ic_temp).tag = "temp"

        // Data Usage: Def False, 14sp (New)
        val dataSwitch = bindSection(R.id.section_data, getString(R.string.section_data_usage), "show_data_usage", false, "size_data", 14f, 10f, 24f, isContent = true, iconResId = R.drawable.ic_data)
        dataSwitch.tag = "data"
        dataSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!hasUsageStatsPermission()) {
                    try {
                        startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    } catch (e: Exception) { }
                    dataSwitch.isChecked = false
                    return@setOnCheckedChangeListener
                }
                if (!checkLimit()) {
                    dataSwitch.isChecked = false
                    return@setOnCheckedChangeListener
                }
            }
            prefs.edit().putBoolean("show_data_usage", isChecked).apply()
            findViewById<View>(R.id.section_data).findViewById<View>(R.id.size_container).visibility = if (isChecked) View.VISIBLE else View.GONE
            updateWidget()
            updateToggleAvailability()
        }

        // Storage: Def False, 14sp (New)
        val storageSwitch = bindSection(R.id.section_storage, getString(R.string.section_storage), "show_storage", true, "size_storage", 14f, 10f, 24f, isContent = true, iconResId = R.drawable.ic_storage)
        storageSwitch.tag = "storage"

        val stepsSwitch = bindSection(R.id.section_steps, getString(R.string.section_steps), "show_steps", false, "size_steps", 14f, 10f, 24f, isContent = true, iconResId = R.drawable.ic_steps)
        stepsSwitch.tag = "steps"
        stepsSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Check permissions (ACTIVITY_RECOGNITION on API 29+, POST_NOTIFICATIONS on API 33+)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val neededPermissions = mutableListOf<String>()
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                        neededPermissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        neededPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    if (neededPermissions.isNotEmpty()) {
                        ActivityCompat.requestPermissions(this, neededPermissions.toTypedArray(), 102)
                        stepsSwitch.isChecked = false // Revert until granted
                        return@setOnCheckedChangeListener
                    }
                }
                if (!checkLimit()) {
                    stepsSwitch.isChecked = false
                    return@setOnCheckedChangeListener
                }
            }
            prefs.edit().putBoolean("show_steps", isChecked).apply()
            
            // Start or stop the Step Counter Service
            val serviceIntent = Intent(this, StepCounterService::class.java)
            if (isChecked) {
                val hasPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }
                if (hasPermission) {
                    startForegroundService(serviceIntent)
                } else {
                    prefs.edit().putBoolean("show_steps", false).apply()
                    findViewById<View>(R.id.section_steps).findViewById<View>(R.id.size_container).visibility = View.GONE
                    updateWidget()
                    updateToggleAvailability()
                    checkAllPermissions()
                    return@setOnCheckedChangeListener
                }
            } else {
                stopService(serviceIntent)
            }
            
            findViewById<View>(R.id.section_steps).findViewById<View>(R.id.size_container).visibility = if (isChecked) View.VISIBLE else View.GONE
            updateWidget()
            updateToggleAvailability()
            checkAllPermissions()
        }
        // Intercept Data Usage toggle for permission check
        dataSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!hasUsageStatsPermission()) {
                    dataSwitch.isChecked = false
                    // Redirect to settings
                    try {
                        startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        com.google.android.material.snackbar.Snackbar.make(
                            findViewById(R.id.fab_update), 
                            getString(R.string.perm_usage_access_title), 
                            com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                        ).show()
                    } catch (e: Exception) {
                        // Fallback
                    }
                    return@setOnCheckedChangeListener
                }
                
                if (!checkLimit()) {
                    dataSwitch.isChecked = false
                    return@setOnCheckedChangeListener
                }
            }
            
            prefs.edit().putBoolean("show_data_usage", isChecked).apply()
            findViewById<View>(R.id.section_data).findViewById<View>(R.id.size_container).visibility = if (isChecked) View.VISIBLE else View.GONE
            updateWidget()
            updateToggleAvailability()
            checkAllPermissions()
        }

        // Screen Time: Def False, 14sp (New)
        val screenTimeSwitch = bindSection(R.id.section_screen_time, getString(R.string.section_screen_time), "show_screen_time", false, "size_screen_time", 14f, 10f, 24f, isContent = true, iconResId = R.drawable.ic_time)
        screenTimeSwitch.tag = "screen_time"
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
                    } catch (e: Exception) { }
                    return@setOnCheckedChangeListener
                }
                if (!checkLimit()) {
                    screenTimeSwitch.isChecked = false
                    return@setOnCheckedChangeListener
                }
            }
            prefs.edit().putBoolean("show_screen_time", isChecked).apply()
            findViewById<View>(R.id.section_screen_time).findViewById<View>(R.id.size_container).visibility = if (isChecked) View.VISIBLE else View.GONE
            updateWidget()
            updateToggleAvailability()
            checkAllPermissions()
        }

        // Events: Def False, 14sp
        val eventsSwitch = bindSection(R.id.section_events, getString(R.string.section_events), "show_events", false, "size_events", 14f, 10f, 18f, isContent = true, iconResId = R.drawable.ic_events)

        // Tasks: Def False, 14sp (New)
        val tasksSwitch = bindSection(R.id.section_tasks, getString(R.string.section_tasks), "show_tasks", false, "size_tasks", 14f, 10f, 18f, isContent = true, iconResId = R.drawable.ic_tasks)



        // Mutual Exclusion: Events vs Tasks
        eventsSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Check if Calendar permission is granted
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
                     ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CALENDAR), 100)
                     eventsSwitch.isChecked = false // Revert until granted
                     return@setOnCheckedChangeListener
                }

                if (checkLimit()) {
                    tasksSwitch.isChecked = false
                    prefs.edit().putBoolean("show_events", true).putBoolean("show_tasks", false).apply()
                    updateWidget()
                    updateToggleAvailability()
                    findViewById<View>(R.id.section_events).findViewById<View>(R.id.size_container).visibility = View.VISIBLE
                    findViewById<View>(R.id.section_tasks).findViewById<View>(R.id.size_container).visibility = View.GONE
                    checkAllPermissions()
                } else {
                    eventsSwitch.isChecked = false // Revert
                }
            } else {
                 prefs.edit().putBoolean("show_events", false).apply()
                 updateWidget()
                 updateToggleAvailability()
                 findViewById<View>(R.id.section_events).findViewById<View>(R.id.size_container).visibility = View.GONE
                 checkAllPermissions()
            }
        }

        tasksSwitch.setOnCheckedChangeListener { _, isChecked ->
             if (isChecked) {
                // Check if Tasks.org is installed
                if (!isAppInstalled("org.tasks")) {
                    tasksSwitch.isChecked = false
                    com.google.android.material.snackbar.Snackbar.make(
                        findViewById(R.id.fab_update), 
                        "Tasks.org app is required for this feature.", 
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                    ).setAction("Install") {
                         try {
                            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=org.tasks")))
                         } catch (e: Exception) {
                            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=org.tasks")))
                         }
                    }.show()
                    return@setOnCheckedChangeListener
                }

                // Check Runtime Permission
                if (ContextCompat.checkSelfPermission(this, "org.tasks.permission.READ_TASKS") != PackageManager.PERMISSION_GRANTED) {
                     ActivityCompat.requestPermissions(this, arrayOf("org.tasks.permission.READ_TASKS"), 101)
                     // Don't uncheck immediately, let them grant it
                }

                if (checkLimit()) {
                    eventsSwitch.isChecked = false
                    prefs.edit().putBoolean("show_tasks", true).putBoolean("show_events", false).apply()
                    updateWidget()
                    updateToggleAvailability()
                     findViewById<View>(R.id.section_tasks).findViewById<View>(R.id.size_container).visibility = View.VISIBLE
                     findViewById<View>(R.id.section_events).findViewById<View>(R.id.size_container).visibility = View.GONE
                     checkAllPermissions()
                } else {
                    tasksSwitch.isChecked = false // Revert
                }
            } else {
                 prefs.edit().putBoolean("show_tasks", false).apply()
                 updateWidget()
                 updateToggleAvailability()
                 findViewById<View>(R.id.section_tasks).findViewById<View>(R.id.size_container).visibility = View.GONE
                 checkAllPermissions()
            }
        }
        
        // Initial visibility fix for mutually exclusive items (since generic bindSection logic is overridden above)
        findViewById<View>(R.id.section_events).findViewById<View>(R.id.size_container).visibility = if (eventsSwitch.isChecked) View.VISIBLE else View.GONE
        findViewById<View>(R.id.section_tasks).findViewById<View>(R.id.size_container).visibility = if (tasksSwitch.isChecked) View.VISIBLE else View.GONE


        // Outline: Def True (Renamed from Glow)
        bindToggle(R.id.section_outline, getString(R.string.section_outline), "show_outline", true, iconResId = R.drawable.ic_outline) { isChecked ->
             val outlineColorSection = findViewById<View>(R.id.section_outline_color)
             val sliders = findViewById<View>(R.id.sliders_outline)
             val idx = prefs.getInt("outline_color_idx", 0)
             
             if (isChecked) {
                 outlineColorSection.visibility = View.VISIBLE
                 sliders.visibility = if (idx == 2) View.VISIBLE else View.GONE
             } else {
                 outlineColorSection.visibility = View.GONE
                 sliders.visibility = View.GONE
             }
        }

        // Dynamic Colors (Android 12+)
        val sectionDynamicColor = findViewById<View>(R.id.section_dynamic_colors)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            sectionDynamicColor.visibility = View.VISIBLE
            bindToggle(R.id.section_dynamic_colors, "Dynamic Colors", "use_dynamic_colors", true, iconResId = R.drawable.ic_palette) { isChecked ->

                 // Hide manual color pickers if dynamic is enabled
                 val sectionPrimary = findViewById<View>(R.id.section_text_color_primary)
                 val sectionSecondary = findViewById<View>(R.id.section_text_color_secondary)
                 val sectionOutlineColor = findViewById<View>(R.id.section_outline_color)
                 val sectionBgColor = findViewById<View>(R.id.section_bg_color)
                 val slidersPrimary = findViewById<View>(R.id.sliders_primary)
                 val slidersSecondary = findViewById<View>(R.id.sliders_secondary)
                 val slidersOutline = findViewById<View>(R.id.sliders_outline)
                 val vis = if (isChecked) View.GONE else View.VISIBLE
                 sectionPrimary.visibility = vis
                 sectionSecondary.visibility = vis
                 sectionOutlineColor.visibility = vis
                 sectionBgColor.visibility = vis
                 slidersPrimary.visibility = View.GONE
                 slidersSecondary.visibility = View.GONE
                 slidersOutline.visibility = View.GONE
                 findViewById<View>(R.id.sliders_bg_color).visibility = View.GONE
                 // Auto-select Default color when dynamic colors is turned on
                 if (isChecked) {
                     prefs.edit()
                         .putInt("text_color_primary_idx", 0)
                         .putInt("text_color_secondary_idx", 0)
                         .putInt("outline_color_idx", 0)
                         .putInt("bg_color_idx", 0)
                         .apply()
                 }
            }
        } else {
            sectionDynamicColor.visibility = View.GONE
        }

        // System Theme: Def False (follows system dark/light mode)
        bindToggle(R.id.section_theme, getString(R.string.section_theme), "use_system_theme", false, iconResId = R.drawable.ic_sun)



        // Background Opacity: Def 100 (Opaque)
        bindSlider(R.id.section_bg_transparency, getString(R.string.section_bg_transparency), "bg_opacity", 100f, 0f, 100f, iconResId = R.drawable.ic_transparency)

        // Background Color
        val colorOptions = listOf(
            getString(R.string.color_default),
            getString(R.string.color_system_accent),
            getString(R.string.color_custom)
        )

        val slidersBgColor = bindColorSliders(R.id.sliders_bg_color, "bg_color")
        bindSelector(R.id.section_bg_color, getString(R.string.section_bg_color), "bg_color_idx", colorOptions, 0, iconResId = R.drawable.ic_palette) { idx ->
             slidersBgColor.visibility = if (idx == 2) View.VISIBLE else View.GONE
        }
        slidersBgColor.visibility = if (prefs.getInt("bg_color_idx", 0) == 2) View.VISIBLE else View.GONE

        // Text Colors
        val slidersPrimary = bindColorSliders(R.id.sliders_primary, "text_color_primary")
        bindSelector(R.id.section_text_color_primary, getString(R.string.section_text_color_primary), "text_color_primary_idx", colorOptions, 0, iconResId = R.drawable.ic_palette) { idx ->
             slidersPrimary.visibility = if (idx == 2) View.VISIBLE else View.GONE
        }
        
        val slidersSecondary = bindColorSliders(R.id.sliders_secondary, "text_color_secondary")
        bindSelector(R.id.section_text_color_secondary, getString(R.string.section_text_color_secondary), "text_color_secondary_idx", colorOptions, 0, iconResId = R.drawable.ic_palette) { idx ->
             slidersSecondary.visibility = if (idx == 2) View.VISIBLE else View.GONE
        }

        // Initial Visibility State
        slidersPrimary.visibility = if (prefs.getInt("text_color_primary_idx", 0) == 2) View.VISIBLE else View.GONE
        slidersSecondary.visibility = if (prefs.getInt("text_color_secondary_idx", 0) == 2) View.VISIBLE else View.GONE
        
        // Outline Color
        val slidersOutline = bindColorSliders(R.id.sliders_outline, "outline_color")
        bindSelector(R.id.section_outline_color, getString(R.string.section_outline_color), "outline_color_idx", colorOptions, 0, iconResId = R.drawable.ic_palette) { idx ->
             slidersOutline.visibility = if (idx == 2) View.VISIBLE else View.GONE
        }
        slidersOutline.visibility = if (prefs.getInt("outline_color_idx", 0) == 2) View.VISIBLE else View.GONE

        // Re-apply dynamic colors hiding after all color sections are initialized
        // (the bindToggle callback fires before these sections exist, so we need this final pass)
        if (prefs.getBoolean("use_dynamic_colors", true) && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            findViewById<View>(R.id.section_text_color_primary).visibility = View.GONE
            findViewById<View>(R.id.section_text_color_secondary).visibility = View.GONE
            findViewById<View>(R.id.section_outline_color).visibility = View.GONE
            findViewById<View>(R.id.section_bg_color).visibility = View.GONE
            slidersPrimary.visibility = View.GONE
            slidersSecondary.visibility = View.GONE
            slidersOutline.visibility = View.GONE
            slidersBgColor.visibility = View.GONE
        }

        // Font Style: Def "Default" (0)
        bindSelector(R.id.section_font, getString(R.string.section_font), "font_style", listOf(
            getString(R.string.font_default), getString(R.string.font_serif), getString(R.string.font_monospace), getString(R.string.font_cursive), 
            getString(R.string.font_condensed), getString(R.string.font_condensed_light), getString(R.string.font_light), getString(R.string.font_medium), 
            getString(R.string.font_black), getString(R.string.font_thin), getString(R.string.font_smallcaps)
        ), 0, iconResId = R.drawable.ic_text_format)
        
        // Enforce limit initially
        updateToggleAvailability()
    }

    private fun bindSection(
        sectionId: Int, 
        title: String, 
        prefShowKey: String, 
        defShow: Boolean, 
        prefSizeKey: String, 
        defSize: Float,
        minSize: Float,
        maxSize: Float,
        isContent: Boolean = false,
        iconResId: Int? = null,
        onChanged: ((Boolean) -> Unit)? = null
    ): SwitchMaterial {
        val section = findViewById<View>(sectionId)
        val tvTitle = section.findViewById<TextView>(R.id.item_title)
        val ivIcon = section.findViewById<android.widget.ImageView>(R.id.item_icon)
        val switch = section.findViewById<SwitchMaterial>(R.id.item_switch)
        val sizeContainer = section.findViewById<View>(R.id.size_container)
        val slider = section.findViewById<Slider>(R.id.item_slider)
        val tvSize = section.findViewById<TextView>(R.id.size_label)

        if (iconResId != null) {
            ivIcon.setImageResource(iconResId)
            ivIcon.visibility = View.VISIBLE
        } else {
             ivIcon.visibility = View.GONE
        }

        tvTitle.text = title

        if (isContent) {
            contentSwitches.add(switch)
        }

        // Load Toggle
        val isShown = prefs.getBoolean(prefShowKey, defShow)
        switch.isChecked = isShown
        sizeContainer.visibility = if (isShown) View.VISIBLE else View.GONE
        onChanged?.invoke(isShown)

        // Common Listener (can be overridden returned switch)
        switch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !checkLimit()) {
                switch.isChecked = false
                return@setOnCheckedChangeListener
            }
            prefs.edit().putBoolean(prefShowKey, isChecked).apply()
            sizeContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
            onChanged?.invoke(isChecked)
            updateWidget()
            if (isContent) updateToggleAvailability()
        }

        // Load Slider
        val currentSize = prefs.getFloat(prefSizeKey, defSize)
        slider.valueFrom = minSize
        slider.valueTo = maxSize
        slider.value = currentSize.coerceIn(minSize, maxSize)
        tvSize.text = "${currentSize.toInt()}"

        slider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                tvSize.text = "${value.toInt()}"
                prefs.edit().putFloat(prefSizeKey, value).apply()
                // Debounce update? For now live is fine
                updateWidget() 
            }
        }
        
        return switch
    }

    private fun bindToggle(
        sectionId: Int,
        title: String,
        prefShowKey: String,
        defShow: Boolean,
        isContent: Boolean = false,
        iconResId: Int? = null,
        onChanged: ((Boolean) -> Unit)? = null
    ) {
        val section = findViewById<View>(sectionId)
        val tvTitle = section.findViewById<TextView>(R.id.item_title)
        val ivIcon = section.findViewById<android.widget.ImageView>(R.id.item_icon)
        val switch = section.findViewById<SwitchMaterial>(R.id.item_switch)
        val sizeContainer = section.findViewById<View>(R.id.size_container)

        tvTitle.text = title
        
        if (iconResId != null) {
            ivIcon.setImageResource(iconResId)
            ivIcon.visibility = View.VISIBLE
        } else {
             ivIcon.visibility = View.GONE
        }
        sizeContainer.visibility = View.GONE

        if (isContent) {
            contentSwitches.add(switch)
        }

        // Load Toggle
        val isShown = prefs.getBoolean(prefShowKey, defShow)
        switch.isChecked = isShown
        onChanged?.invoke(isShown)

        switch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !checkLimit()) {
                switch.isChecked = false
                return@setOnCheckedChangeListener
            }
            prefs.edit().putBoolean(prefShowKey, isChecked).apply()
            onChanged?.invoke(isChecked)
            updateWidget()
            if (isContent) updateToggleAvailability()
        }
    }


    private fun bindSlider(
        sectionId: Int,
        title: String,
        prefKey: String,
        defValue: Float,
        minValue: Float,
        maxValue: Float,
        iconResId: Int? = null
    ) {
        val section = findViewById<View>(sectionId)
        val tvTitle = section.findViewById<TextView>(R.id.item_title)
        val ivIcon = section.findViewById<android.widget.ImageView>(R.id.item_icon)
        val switch = section.findViewById<SwitchMaterial>(R.id.item_switch)
        val sizeContainer = section.findViewById<View>(R.id.size_container)
        val slider = section.findViewById<Slider>(R.id.item_slider)
        val tvSize = section.findViewById<TextView>(R.id.size_label)

        tvTitle.text = title
        
        if (iconResId != null) {
            ivIcon.setImageResource(iconResId)
            ivIcon.visibility = View.VISIBLE
        } else {
             ivIcon.visibility = View.GONE
        }
        
        // Hide switch, show slider container explicitly
        switch.visibility = View.GONE
        sizeContainer.visibility = View.VISIBLE

        // Load Slider
        val currentValue = prefs.getFloat(prefKey, defValue)
        slider.valueFrom = minValue
        slider.valueTo = maxValue
        slider.value = currentValue.coerceIn(minValue, maxValue)
        tvSize.text = "${currentValue.toInt()}%"

        slider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                tvSize.text = "${value.toInt()}%"
                prefs.edit().putFloat(prefKey, value).apply()
                // Debounce?
                updateWidget()
            }
        }
    }

    private fun bindSelector(
        sectionId: Int, 
        title: String, 
        prefKey: String, 
        options: List<String>, 
        defaultIdx: Int,
        iconResId: Int? = null,
        onSelectionChanged: ((Int) -> Unit)? = null
    ) {
        val section = findViewById<View>(sectionId)
        val tvTitle = section.findViewById<TextView>(R.id.item_title)
        val ivIcon = section.findViewById<android.widget.ImageView>(R.id.item_icon)
        val autoCompleteTextView = section.findViewById<android.widget.AutoCompleteTextView>(R.id.item_value)

        tvTitle.text = title
        
        if (iconResId != null) {
             ivIcon.setImageResource(iconResId)
             ivIcon.visibility = View.VISIBLE
        } else {
             ivIcon.visibility = View.GONE
        }
        
        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, options)
        autoCompleteTextView.setAdapter(adapter)

        // For string preference
        if (prefKey == "world_clock_zone_str") {
            val currentVal = prefs.getString(prefKey, "UTC") ?: "UTC"
            autoCompleteTextView.setText(currentVal, false)
            
            autoCompleteTextView.setOnItemClickListener { _, _, position, _ ->
                val selected = options.getOrElse(position) { "UTC" }
                prefs.edit().putString(prefKey, selected).apply()
                updateWidget()
            }
        } else {
            // Index based (legacy/others)
            val currentIdx = prefs.getInt(prefKey, defaultIdx)
            autoCompleteTextView.setText(options.getOrElse(currentIdx) { options[defaultIdx] }, false)

            autoCompleteTextView.setOnItemClickListener { _, _, position, _ ->
                prefs.edit().putInt(prefKey, position).apply()
                updateWidget()
                onSelectionChanged?.invoke(position)
                section.requestFocus()
                autoCompleteTextView.clearFocus()
            }
        }
    }

    private fun bindColorSliders(sectionId: Int, prefPrefix: String): View {
        val section = findViewById<View>(sectionId)
        val sliderRed = section.findViewById<Slider>(R.id.slider_red)
        val sliderGreen = section.findViewById<Slider>(R.id.slider_green)
        val sliderBlue = section.findViewById<Slider>(R.id.slider_blue)
        val valRed = section.findViewById<TextView>(R.id.val_red)
        val valGreen = section.findViewById<TextView>(R.id.val_green)
        val valBlue = section.findViewById<TextView>(R.id.val_blue)
        val preview = section.findViewById<View>(R.id.color_preview)

        val r = prefs.getInt("${prefPrefix}_r", 255)
        val g = prefs.getInt("${prefPrefix}_g", 255)
        val b = prefs.getInt("${prefPrefix}_b", 255)

        fun updatePreview() {
            val color = android.graphics.Color.rgb(sliderRed.value.toInt(), sliderGreen.value.toInt(), sliderBlue.value.toInt())
            preview.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
            
            valRed.text = sliderRed.value.toInt().toString()
            valGreen.text = sliderGreen.value.toInt().toString()
            valBlue.text = sliderBlue.value.toInt().toString()
        }

        sliderRed.value = r.toFloat()
        sliderGreen.value = g.toFloat()
        sliderBlue.value = b.toFloat()
        updatePreview()

        val listener = com.google.android.material.slider.Slider.OnChangeListener { _, _, fromUser ->
            if (fromUser) {
                updatePreview()
                prefs.edit()
                    .putInt("${prefPrefix}_r", sliderRed.value.toInt())
                    .putInt("${prefPrefix}_g", sliderGreen.value.toInt())
                    .putInt("${prefPrefix}_b", sliderBlue.value.toInt())
                    .apply()
                // Debounce widget update to avoid too many broadcasts? 
                // For now, let's update on drag end if possible, or live. Live might be heavy.
                // But user expects feedback.
                updateWidget()
            }
        }

        sliderRed.addOnChangeListener(listener)
        sliderGreen.addOnChangeListener(listener)
        sliderBlue.addOnChangeListener(listener)

        return section
    }

    private fun checkLimit(): Boolean {
        // Global limit removed per user request

        // Subset Limit: Battery, Temp, Data, Storage (Max 4 allowed now to fit stack)
        val subsetCount = contentSwitches.count { 
            it.isChecked && (it.tag == "battery" || it.tag == "temp" || it.tag == "data" || it.tag == "storage") 
        }
        
        if (subsetCount > 4) {
             com.google.android.material.snackbar.Snackbar.make(
                findViewById(R.id.fab_update), 
                getString(R.string.error_max_subset_items), 
                com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
            ).show()
            return false
        }

        return true
    }
    
    // Check usage stats permission
    private fun hasUsageStatsPermission(): Boolean {
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

    private fun updateToggleAvailability() {
        // Limit removed
        // Ensure all are enabled
        for (switch in contentSwitches) {
            switch.isEnabled = true
            switch.alpha = 1.0f
        }
    }

    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun updateWidget() {
        // Animation: Subtle Outline Shine
        val fab = findViewById<ExtendedFloatingActionButton>(R.id.fab_update)
        
        // Get dynamic colors
        // val colorSurface = com.google.android.material.color.MaterialColors.getColor(fab, com.google.android.material.R.attr.colorSurface)
        val colorPrimary = com.google.android.material.color.MaterialColors.getColor(fab, com.google.android.material.R.attr.colorPrimary)
        val colorTransparent = android.graphics.Color.TRANSPARENT

        val strokeAnimator = android.animation.ValueAnimator.ofArgb(colorTransparent, colorPrimary, colorTransparent)
        strokeAnimator.duration = 1000
        strokeAnimator.addUpdateListener { animator ->
            fab.strokeColor = android.content.res.ColorStateList.valueOf(animator.animatedValue as Int)
        }
        strokeAnimator.start()

        // Trigger widget update by sending broadcast
        val intent = Intent(this, AwidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            // Get all IDs
            val ids = AppWidgetManager.getInstance(application).getAppWidgetIds(ComponentName(application, AwidgetProvider::class.java))
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        sendBroadcast(intent)
    }
}
