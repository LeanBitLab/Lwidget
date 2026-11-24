package com.leanbitlab.lwidget

import android.content.Context
import android.os.Looper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Safety net for MainActivity refactors: the settings screen must inflate and bind every
 * section without throwing, and the key controls must still exist afterwards.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MainActivitySmokeTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("com.leanbitlab.lwidget.PREFS", Context.MODE_PRIVATE)
            .edit().clear().putBoolean("is_first_launch", false).commit()
    }

    @Test
    fun launchesAndBindsAllSections() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        assertTrue("activity should not have finished", !activity.isFinishing)
        activity.ensureAllTabsReady()
        listOf(
            R.id.row_update_interval,
            R.id.fab_update,
            R.id.nested_scroll_view,
            R.id.tv_changelog_version,
        ).forEach { id -> assertNotNull("missing view $id", activity.findViewById<View>(id)) }
    }

    @Test
    fun onlyTheSelectedTabIsInflatedBeforeTheMainThreadIsIdle() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().get()

        // Style is the default tab; the others are still ViewStubs until idle or needed.
        assertNotNull(activity.findViewById<View>(R.id.container_tab_style))
        assertTrue(activity.findViewById<View>(R.id.container_tab_system) == null)
        assertNotNull(activity.findViewById<View>(R.id.stub_tab_system))

        // Once the main thread goes idle the remaining tabs are inflated and bound.
        shadowOf(Looper.getMainLooper()).idle()
        listOf(
            R.id.container_tab_content,
            R.id.container_tab_layout,
            R.id.container_tab_system,
        ).forEach { id -> assertNotNull("tab $id should be ready once idle", activity.findViewById<View>(id)) }
    }

    @Test
    fun ensureAllTabsReadyInflatesAndBindsEveryTab() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        activity.ensureAllTabsReady()
        activity.ensureAllTabsReady() // idempotent

        listOf(
            R.id.container_tab_style,
            R.id.container_tab_content,
            R.id.container_tab_layout,
            R.id.container_tab_system,
        ).forEach { id -> assertNotNull("missing tab $id", activity.findViewById<View>(id)) }
        // Only the selected tab is visible.
        assertTrue(activity.findViewById<View>(R.id.container_tab_style).visibility == View.VISIBLE)
        assertTrue(activity.findViewById<View>(R.id.container_tab_content).visibility == View.GONE)
        assertTrue(activity.findViewById<View>(R.id.container_tab_system).visibility == View.GONE)
    }

    @Test
    fun launchesWithEachSavedTab() {
        for (tab in 0..3) {
            context.getSharedPreferences("com.leanbitlab.lwidget.PREFS", Context.MODE_PRIVATE)
                .edit().putInt("selected_settings_tab", tab).commit()
            val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
            assertTrue("tab $tab should launch", !activity.isFinishing)
            activity.ensureAllTabsReady()
            val ids = intArrayOf(R.id.container_tab_style, R.id.container_tab_content, R.id.container_tab_layout, R.id.container_tab_system)
            ids.forEachIndexed { i, id ->
                val expected = if (i == tab) View.VISIBLE else View.GONE
                assertTrue("tab $tab: container $i visibility", activity.findViewById<View>(id).visibility == expected)
            }
        }
    }

    @Test
    fun firstLaunchRedirectsToSetup() {
        context.getSharedPreferences("com.leanbitlab.lwidget.PREFS", Context.MODE_PRIVATE)
            .edit().putBoolean("is_first_launch", true).commit()

        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        assertTrue(activity.isFinishing)
    }

    @Test
    fun survivesRecreate() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        controller.recreate()

        assertTrue(!controller.get().isFinishing)
    }

    @Test
    @Config(sdk = [33], qualifiers = "w900dp-h600dp")
    fun launchesOnWideScreen() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        assertTrue(!activity.isFinishing)
        assertNotNull(activity.findViewById<View>(R.id.nested_scroll_view))
    }
}
