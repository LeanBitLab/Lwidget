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
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import java.util.Locale
import android.os.Bundle
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import android.view.ViewGroup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.tabs.TabLayout
import android.view.MotionEvent
import androidx.core.widget.NestedScrollView
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.google.android.material.appbar.AppBarLayout

class MainActivity : AppCompatActivity() {

    internal lateinit var prefs: SharedPreferences
    internal val contentSwitches = mutableListOf<SwitchMaterial>()
    internal var clockAppPackages = listOf("default")
    internal var calendarAppPackages = listOf("default")
    private var currentWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this)
        setContentView(R.layout.activity_main)

        val globalPrefs = getSharedPreferences("com.leanbitlab.lwidget.PREFS", Context.MODE_PRIVATE)

        if (globalPrefs.getBoolean("is_first_launch", true)) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }

        var widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val ids = appWidgetManager.getAppWidgetIds(ComponentName(this, AwidgetProvider::class.java))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID && ids.size == 1) {
            widgetId = ids[0]
        }
        currentWidgetId = widgetId

        prefs = if (currentWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            val wPrefs = getSharedPreferences("com.leanbitlab.lwidget.PREFS_$currentWidgetId", Context.MODE_PRIVATE)
            FallbackPreferences(wPrefs, globalPrefs)
        } else {
            globalPrefs
        }

        // Update titles to reflect configured widget ID
        val titleApp = findViewById<TextView>(R.id.title_app)
        val subtitleApp = findViewById<TextView>(R.id.subtitle_app)

        if (currentWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            titleApp.text = getString(R.string.widget_title_numbered, currentWidgetId)
            subtitleApp.text = getString(R.string.widget_selector_numbered, currentWidgetId)
        } else {
            titleApp.text = getString(R.string.app_name)
            subtitleApp.text = getString(R.string.widget_default_settings_selector)
        }

        subtitleApp.setOnClickListener {
            val options = mutableListOf<String>()
            val optionIds = mutableListOf<Int>()

            options.add(getString(R.string.widget_default_settings))
            optionIds.add(AppWidgetManager.INVALID_APPWIDGET_ID)

            ids.forEachIndexed { index, id ->
                options.add(getString(R.string.widget_option_numbered, index + 1, id))
                optionIds.add(id)
            }

            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.widget_picker_title))
                .setItems(options.toTypedArray()) { _, which ->
                    val selectedId = optionIds[which]
                    val newIntent = Intent(this, MainActivity::class.java).apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, selectedId)
                    }
                    onNewIntent(newIntent)
                }
                .show()
        }

        checkAllPermissions()
        StepCounterService.startServiceIfEnabled(this)
        setupSections()
        setupPreviewWallpaper()
        setupTabLayout()
        setupGlobalSettingsSearch()
        updateLivePreview()
        
        // Advanced Section
        bindSlider(R.id.row_update_interval, getString(R.string.row_update_interval), "update_interval", 15f, 1f, 60f, "m")
        
        // Setup Changelog
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            getString(R.string.version_unknown)
        }
        val tvVersion = findViewById<TextView>(R.id.tv_changelog_version)
        tvVersion.text = getString(R.string.changelog_version, versionName)

        findViewById<View>(R.id.tv_website_link)?.setOnClickListener {
            CustomTabsIntent.Builder().build().launchUrl(this@MainActivity, android.net.Uri.parse("https://leanbitlab.github.io/LeanBitLab/"))
        }

        findViewById<View>(R.id.tv_github_link).setOnClickListener {
            CustomTabsIntent.Builder().build().launchUrl(this@MainActivity, android.net.Uri.parse("https://github.com/LeanBitLab/Lwidget"))
        }

        findViewById<View>(R.id.tv_privacy_policy).setOnClickListener {
            CustomTabsIntent.Builder().build().launchUrl(this@MainActivity, android.net.Uri.parse("https://github.com/LeanBitLab/Lwidget/wiki/Privacy-Policy"))
        }

        
        val fab = findViewById<ExtendedFloatingActionButton>(R.id.fab_update)
        fab.setOnClickListener {
            updateWidget()
            updateLivePreview()
        }

        val rootLayout = findViewById<androidx.coordinatorlayout.widget.CoordinatorLayout>(R.id.coordinator_layout)

        // Unified WindowInsets handler: maintains solid status bar background and clips scrolling content cleanly
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, windowInsets ->
            val statusBars = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBars = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())

            rootLayout.setPadding(0, statusBars.top, 0, 0)
            fab.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = navBars.bottom + (24 * resources.displayMetrics.density).toInt()
                rightMargin = navBars.right + (24 * resources.displayMetrics.density).toInt()
            }
            windowInsets
        }

        val nestedScrollView = findViewById<NestedScrollView>(R.id.nested_scroll_view)
        val scrollContent = findViewById<View>(R.id.nested_scroll_content)
        nestedScrollView?.post {
            val topBarHeight = findViewById<View>(R.id.top_bar_container)?.height ?: 0
            val targetMinHeight = nestedScrollView.height + topBarHeight
            scrollContent?.minimumHeight = targetMinHeight
        }

        nestedScrollView?.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            if (scrollY > oldScrollY + 12 && fab.isExtended) {
                fab.shrink()
            } else if (scrollY < oldScrollY - 12 && !fab.isExtended) {
                fab.extend()
            }
        }

        constrainContentWidth()

    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun setupPreviewWallpaper() {
        val wallpaperView = findViewById<ImageView>(R.id.preview_wallpaper) ?: return
        try {
            val wallpaperManager = android.app.WallpaperManager.getInstance(this)
            val wallpaperDrawable = wallpaperManager.drawable
            if (wallpaperDrawable != null) {
                wallpaperView.setImageDrawable(wallpaperDrawable)
                return
            }
        } catch (e: Exception) {
            // Fallback to themed gradient
        }

        val isSystemInNightMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val themeMode = prefs.getInt("theme_mode", if (prefs.getBoolean("use_system_theme", true)) 0 else 2)
        val useLightTheme = when (themeMode) {
            0 -> !isSystemInNightMode
            1 -> true
            2 -> false
            else -> !isSystemInNightMode
        }

        val useDynamicColors = prefs.getBoolean("use_dynamic_colors", true)
        val bgColorIdx = prefs.getInt("bg_color_idx", 0)
        val bgOpacity = prefs.getFloat("bg_opacity", 85f)
        val textPrimaryIdx = prefs.getInt("text_color_primary_idx", 0)

        val isWidgetDark = if (useDynamicColors) {
            !useLightTheme
        } else {
            val bgDark = when (bgColorIdx) {
                0 -> !useLightTheme
                1 -> false
                2 -> {
                    val r = prefs.getInt("bg_color_r", 255)
                    val g = prefs.getInt("bg_color_g", 255)
                    val b = prefs.getInt("bg_color_b", 255)
                    (0.299 * r + 0.587 * g + 0.114 * b) < 128
                }
                else -> !useLightTheme
            }
            if (bgOpacity < 20f && textPrimaryIdx == 2) {
                val tr = prefs.getInt("text_color_primary_r", 255)
                val tg = prefs.getInt("text_color_primary_g", 255)
                val tb = prefs.getInt("text_color_primary_b", 255)
                val textIsWhite = (0.299 * tr + 0.587 * tg + 0.114 * tb) > 180
                textIsWhite || bgDark
            } else {
                bgDark
            }
        }

        wallpaperView.setImageResource(if (isWidgetDark) R.drawable.bg_preview_wallpaper_dark else R.drawable.bg_preview_wallpaper_light)
    }

    private fun setupTabLayout() {
        val tabLayout = findViewById<TabLayout>(R.id.tab_layout) ?: return
        val containerStyle = findViewById<View>(R.id.container_tab_style)
        val containerContent = findViewById<View>(R.id.container_tab_content)
        val containerLayout = findViewById<View>(R.id.container_tab_layout)
        val containerSystem = findViewById<View>(R.id.container_tab_system)

        val tabContainers = listOf(containerStyle, containerContent, containerLayout, containerSystem)

        tabLayout.removeAllTabs()
        tabLayout.addTab(tabLayout.newTab().setText(R.string.category_appearance).setIcon(R.drawable.ic_palette))
        tabLayout.addTab(tabLayout.newTab().setText(R.string.category_content).setIcon(R.drawable.ic_tasks))
        tabLayout.addTab(tabLayout.newTab().setText(R.string.header_reorder).setIcon(R.drawable.ic_reorder))
        tabLayout.addTab(tabLayout.newTab().setText(R.string.category_system).setIcon(R.drawable.ic_tune))

        val savedTab = prefs.getInt("selected_settings_tab", 0).coerceIn(0, 3)
        tabLayout.getTabAt(savedTab)?.select()
        tabContainers.forEachIndexed { index, container ->
            container?.visibility = if (index == savedTab) View.VISIBLE else View.GONE
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val searchInput = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.search_settings_input)
                if (!searchInput?.text.isNullOrEmpty()) {
                    searchInput?.setText("")
                }
                prefs.edit().putInt("selected_settings_tab", tab.position).apply()
                tabContainers.forEachIndexed { index, container ->
                    container?.visibility = if (index == tab.position) View.VISIBLE else View.GONE
                }
                if (tab.position == 2) {
                    bindReorderSection()
                }
                findViewById<NestedScrollView>(R.id.nested_scroll_view)?.scrollTo(0, 0)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    internal fun updateLivePreview() {
        val previewContainer = findViewById<android.widget.FrameLayout>(R.id.preview_container)
        try {
            val remoteViews = AwidgetProvider.Companion.buildAppWidgetRemoteViews(this, currentWidgetId, UpdateMode.FULL)
            val view = remoteViews.apply(applicationContext, previewContainer)
            previewContainer.removeAllViews()
            previewContainer.addView(view)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        // onCreate returns early (first launch -> SetupActivity); nothing is bound in that case.
        if (!::prefs.isInitialized) return
        // Re-check permissions when returning (especially for Data Usage settings)
        checkAllPermissions()
        StepCounterService.startServiceIfEnabled(this)
        setupPreviewWallpaper()
        // Force a full widget update every time the app is opened
        updateWidget()
        // Update battery optimization switch state
        updateBatteryOptimizationSwitch()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        checkAllPermissions()
        if (requestCode == 101) {
             // Task permission result - trigger update
             if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                 updateWidget()
             }
        } else if (requestCode == 102) {
             // Step Counter permission result
             val hasActivityPerm = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q ||
                 ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
             if (hasActivityPerm) {
                 prefs.edit().putBoolean("show_steps", true).apply()
                 val globalPrefs = getSharedPreferences("com.leanbitlab.lwidget.PREFS", Context.MODE_PRIVATE)
                 globalPrefs.edit().putBoolean("show_steps", true).apply()
                 val stepsToggle = findViewById<View>(R.id.row_steps_toggle)
                 stepsToggle?.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.row_switch)?.isChecked = true
                 findViewById<View>(R.id.row_steps_size)?.visibility = View.VISIBLE
                 findViewById<View>(R.id.row_steps_bold)?.visibility = View.VISIBLE
                 StepCounterService.startServiceIfEnabled(this)
                 updateWidget()
                 updateToggleAvailability()
             }
        } else if (requestCode == 103) {
             // Breezy Weather permission result
             if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                 prefs.edit().putBoolean("show_weather_condition", true).apply()
                 findViewById<View>(R.id.row_weather_toggle).findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.row_switch).isChecked = true
                 findViewById<View>(R.id.row_weather_size).visibility = View.VISIBLE
                 updateWidget()
                 updateToggleAvailability()

                 // Show Gadgetbridge module prompt
                 showWeatherSetupHint()
             }
        }
    }

    // ===== FOLDED SECTION HELPERS =====

    internal val accordionViews = mutableMapOf<String, View>()
    internal val accordionHeaders = mutableMapOf<String, View>()
    internal val nestedViews = mutableMapOf<String, View>()
    internal val nestedHeaders = mutableMapOf<String, View>()

    private fun setupSections() {
        contentSwitches.clear()

        val zoneIds = java.time.ZoneId.getAvailableZoneIds().sorted()
        val dateFormatOptions = listOf(getString(R.string.date_format_full), getString(R.string.date_format_short), getString(R.string.date_format_numeric))
        val timeFormatOptions = listOf(getString(R.string.format_12h), getString(R.string.format_24h))
        val colorOptions = listOf(getString(R.string.color_default), getString(R.string.color_system_accent), getString(R.string.color_custom))

        setupTimeSection(timeFormatOptions)
        setupNextAlarmSection()
        setupWorldClockSection(zoneIds)
        setupDateSection(dateFormatOptions)
        setupBatterySection()
        setupTempSection()
        setupWeatherSection()
        setupDataUsageSection()
        setupStorageSection()
        setupRamSection()
        setupStepsSection()
        setupScreenTimeSection()
        setupKeepAliveSection()
        setupBatteryOptimizationSection()
        setupEventsAndTasksSections()
        setupThemeSection(colorOptions)
        setupLanguageSection()
        
        // System sections
        bindCategoryFoldable(R.id.header_language, R.id.content_language, getString(R.string.section_app_language), R.drawable.ic_language, "section_language_expanded")
        bindCategoryFoldable(R.id.header_advanced, R.id.content_advanced, getString(R.string.header_advanced), R.drawable.ic_tune, "section_advanced_expanded")
        bindCategoryFoldable(R.id.header_permissions, R.id.content_permissions, getString(R.string.header_permissions), R.drawable.ic_shield, "section_permissions_expanded")
        bindCategoryFoldable(R.id.header_about, R.id.content_about, getString(R.string.header_about), R.drawable.ic_info, "section_about_expanded")
        bindNestedCard(R.id.header_changelog, "", R.id.content_changelog, "section_changelog_expanded", R.id.chevron_changelog)
    }



    internal fun updateWidget() {
        setupPreviewWallpaper()
        updateLivePreview()
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
