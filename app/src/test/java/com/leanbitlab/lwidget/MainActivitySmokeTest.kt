package com.leanbitlab.lwidget

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
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
        listOf(
            R.id.row_update_interval,
            R.id.fab_update,
            R.id.nested_scroll_view,
            R.id.tv_changelog_version,
        ).forEach { id -> assertNotNull("missing view $id", activity.findViewById<View>(id)) }
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
}
