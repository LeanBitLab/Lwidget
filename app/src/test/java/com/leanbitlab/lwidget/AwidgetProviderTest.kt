package com.leanbitlab.lwidget

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class AwidgetProviderTest {

    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var prefs: SharedPreferences

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun `resolveColor dynamic colors ON SDK S primary light`() {
        `when`(context.getColor(android.R.color.system_accent1_800)).thenReturn(1001)
        val color = AwidgetProvider.resolveColor(
            context = context,
            prefs = prefs,
            useDynamicColors = true,
            idx = 0,
            isPrimary = true,
            isLight = true,
            sdkInt = Build.VERSION_CODES.S
        )
        assertEquals(1001, color)
    }

    @Test
    fun `resolveColor dynamic colors ON SDK S primary dark`() {
        `when`(context.getColor(android.R.color.system_accent1_50)).thenReturn(1002)
        val color = AwidgetProvider.resolveColor(
            context = context,
            prefs = prefs,
            useDynamicColors = true,
            idx = 0,
            isPrimary = true,
            isLight = false,
            sdkInt = Build.VERSION_CODES.S
        )
        assertEquals(1002, color)
    }

    @Test
    fun `resolveColor dynamic colors ON SDK S secondary light`() {
        `when`(context.getColor(android.R.color.system_neutral2_600)).thenReturn(1003)
        val color = AwidgetProvider.resolveColor(
            context = context,
            prefs = prefs,
            useDynamicColors = true,
            idx = 0,
            isPrimary = false,
            isLight = true,
            sdkInt = Build.VERSION_CODES.S
        )
        assertEquals(1003, color)
    }

    @Test
    fun `resolveColor dynamic colors ON SDK S secondary dark`() {
        `when`(context.getColor(android.R.color.system_neutral2_300)).thenReturn(1004)
        val color = AwidgetProvider.resolveColor(
            context = context,
            prefs = prefs,
            useDynamicColors = true,
            idx = 0,
            isPrimary = false,
            isLight = false,
            sdkInt = Build.VERSION_CODES.S
        )
        assertEquals(1004, color)
    }

    @Test
    fun `resolveColor idx 0 primary light`() {
        `when`(context.getColor(R.color.widget_text_light)).thenReturn(2001)
        val color = AwidgetProvider.resolveColor(
            context = context,
            prefs = prefs,
            useDynamicColors = false,
            idx = 0,
            isPrimary = true,
            isLight = true,
            sdkInt = Build.VERSION_CODES.R
        )
        assertEquals(2001, color)
    }

    @Test
    fun `resolveColor idx 0 primary dark`() {
        val color = AwidgetProvider.resolveColor(
            context = context,
            prefs = prefs,
            useDynamicColors = false,
            idx = 0,
            isPrimary = true,
            isLight = false,
            sdkInt = Build.VERSION_CODES.R
        )
        assertEquals(Color.WHITE, color)
    }

    @Test
    fun `resolveColor idx 0 secondary light`() {
        `when`(context.getColor(R.color.widget_text_secondary_light)).thenReturn(2002)
        val color = AwidgetProvider.resolveColor(
            context = context,
            prefs = prefs,
            useDynamicColors = false,
            idx = 0,
            isPrimary = false,
            isLight = true,
            sdkInt = Build.VERSION_CODES.R
        )
        assertEquals(2002, color)
    }

    @Test
    fun `resolveColor idx 0 secondary dark`() {
        val expectedColor = Color.parseColor("#CCFFFFFF")
        val color = AwidgetProvider.resolveColor(
            context = context,
            prefs = prefs,
            useDynamicColors = false,
            idx = 0,
            isPrimary = false,
            isLight = false,
            sdkInt = Build.VERSION_CODES.R
        )
        assertEquals(expectedColor, color)
    }

    @Test
    fun `resolveColor idx 1 SDK S`() {
        `when`(context.getColor(android.R.color.system_accent1_500)).thenReturn(3001)
        val color = AwidgetProvider.resolveColor(
            context = context,
            prefs = prefs,
            useDynamicColors = false,
            idx = 1,
            isPrimary = true,
            isLight = true,
            sdkInt = Build.VERSION_CODES.S
        )
        assertEquals(3001, color)
    }

    @Test
    fun `resolveColor idx 1 SDK R`() {
        val color = AwidgetProvider.resolveColor(
            context = context,
            prefs = prefs,
            useDynamicColors = false,
            idx = 1,
            isPrimary = true,
            isLight = true,
            sdkInt = Build.VERSION_CODES.R
        )
        assertEquals(Color.CYAN, color)
    }

    @Test
    fun `resolveColor idx 2 primary`() {
        `when`(prefs.getInt("text_color_primary_r", 255)).thenReturn(255)
        `when`(prefs.getInt("text_color_primary_g", 255)).thenReturn(0)
        `when`(prefs.getInt("text_color_primary_b", 255)).thenReturn(0)

        val color = AwidgetProvider.resolveColor(
            context = context,
            prefs = prefs,
            useDynamicColors = false,
            idx = 2,
            isPrimary = true,
            isLight = true,
            sdkInt = Build.VERSION_CODES.R
        )
        assertEquals(Color.rgb(255, 0, 0), color)
    }

    @Test
    fun `resolveColor idx 2 secondary`() {
        `when`(prefs.getInt("text_color_secondary_r", 255)).thenReturn(0)
        `when`(prefs.getInt("text_color_secondary_g", 255)).thenReturn(255)
        `when`(prefs.getInt("text_color_secondary_b", 255)).thenReturn(0)

        val color = AwidgetProvider.resolveColor(
            context = context,
            prefs = prefs,
            useDynamicColors = false,
            idx = 2,
            isPrimary = false,
            isLight = true,
            sdkInt = Build.VERSION_CODES.R
        )
        assertEquals(Color.rgb(0, 255, 0), color)
    }

    @Test
    fun `resolveColor idx else primary`() {
        val color = AwidgetProvider.resolveColor(
            context = context,
            prefs = prefs,
            useDynamicColors = false,
            idx = 99,
            isPrimary = true,
            isLight = true,
            sdkInt = Build.VERSION_CODES.R
        )
        assertEquals(Color.WHITE, color)
    }

    @Test
    fun `resolveColor idx else secondary`() {
        val expectedColor = Color.parseColor("#CCFFFFFF")
        val color = AwidgetProvider.resolveColor(
            context = context,
            prefs = prefs,
            useDynamicColors = false,
            idx = 99,
            isPrimary = false,
            isLight = true,
            sdkInt = Build.VERSION_CODES.R
        )
        assertEquals(expectedColor, color)
    }
}
