package com.leanbitlab.lwidget

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FallbackPreferencesTest {

    private lateinit var global: SharedPreferences
    private lateinit var widget: SharedPreferences
    private lateinit var prefs: FallbackPreferences

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        global = context.getSharedPreferences("test_global", Context.MODE_PRIVATE)
        widget = context.getSharedPreferences("test_widget", Context.MODE_PRIVATE)
        global.edit().clear().commit()
        widget.edit().clear().commit()
        prefs = FallbackPreferences(widget, global)
    }

    @Test
    fun readsFromGlobalWhenWidgetHasNoValue() {
        global.edit().putBoolean("show_battery", true).putInt("size", 14).commit()

        assertTrue(prefs.getBoolean("show_battery", false))
        assertEquals(14, prefs.getInt("size", 0))
    }

    @Test
    fun widgetValueOverridesGlobal() {
        global.edit().putString("date_format", "global").putFloat("update_interval", 15f).commit()
        widget.edit().putString("date_format", "widget").putFloat("update_interval", 5f).commit()

        assertEquals("widget", prefs.getString("date_format", null))
        assertEquals(5f, prefs.getFloat("update_interval", 0f), 0f)
    }

    @Test
    fun widgetFalseOverridesGlobalTrue() {
        global.edit().putBoolean("show_steps", true).commit()
        widget.edit().putBoolean("show_steps", false).commit()

        assertFalse(prefs.getBoolean("show_steps", true))
    }

    @Test
    fun returnsDefaultWhenNowhere() {
        assertEquals("def", prefs.getString("missing", "def"))
        assertEquals(7L, prefs.getLong("missing", 7L))
        assertFalse(prefs.contains("missing"))
    }

    @Test
    fun writesGoOnlyToWidgetPrefs() {
        global.edit().putInt("size", 14).commit()

        prefs.edit().putInt("size", 20).commit()

        assertEquals(20, widget.getInt("size", 0))
        assertEquals(14, global.getInt("size", 0))
        assertEquals(20, prefs.getInt("size", 0))
    }

    @Test
    fun removingWidgetValueFallsBackToGlobal() {
        global.edit().putInt("size", 14).commit()
        prefs.edit().putInt("size", 20).commit()

        prefs.edit().remove("size").commit()

        assertEquals(14, prefs.getInt("size", 0))
    }

    @Test
    fun getAllMergesWithWidgetWinning() {
        global.edit().putString("a", "g").putString("b", "g").commit()
        widget.edit().putString("b", "w").putString("c", "w").commit()

        val all = prefs.all
        assertEquals("g", all["a"])
        assertEquals("w", all["b"])
        assertEquals("w", all["c"])
        assertTrue(prefs.contains("a") && prefs.contains("c"))
    }
}
