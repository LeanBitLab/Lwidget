package com.leanbitlab.lwidget

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class ColorResolverTest {

    @Test
    fun testResolveColor_DynamicOn_Primary_Light_SdkS() {
        val mockContext = mock<Context> {
            on { getColor(android.R.color.system_accent1_800) } doReturn 0x112233
        }
        val mockPrefs = mock<SharedPreferences>()

        val result = ColorResolver.resolveColor(
            context = mockContext,
            prefs = mockPrefs,
            useDynamicColors = true,
            idx = 0,
            isPrimary = true,
            isLight = true,
            sdkInt = Build.VERSION_CODES.S
        )

        assertEquals(0x112233, result)
    }

    @Test
    fun testResolveColor_DynamicOn_Primary_Dark_SdkS() {
        val mockContext = mock<Context> {
            on { getColor(android.R.color.system_accent1_50) } doReturn 0x223344
        }
        val mockPrefs = mock<SharedPreferences>()

        val result = ColorResolver.resolveColor(
            context = mockContext,
            prefs = mockPrefs,
            useDynamicColors = true,
            idx = 0,
            isPrimary = true,
            isLight = false,
            sdkInt = Build.VERSION_CODES.S
        )

        assertEquals(0x223344, result)
    }

    @Test
    fun testResolveColor_DynamicOn_Secondary_Light_SdkS() {
        val mockContext = mock<Context> {
            on { getColor(android.R.color.system_neutral2_600) } doReturn 0x334455
        }
        val mockPrefs = mock<SharedPreferences>()

        val result = ColorResolver.resolveColor(
            context = mockContext,
            prefs = mockPrefs,
            useDynamicColors = true,
            idx = 0,
            isPrimary = false,
            isLight = true,
            sdkInt = Build.VERSION_CODES.S
        )

        assertEquals(0x334455, result)
    }

    @Test
    fun testResolveColor_DynamicOn_Secondary_Dark_SdkS() {
        val mockContext = mock<Context> {
            on { getColor(android.R.color.system_neutral2_300) } doReturn 0x445566
        }
        val mockPrefs = mock<SharedPreferences>()

        val result = ColorResolver.resolveColor(
            context = mockContext,
            prefs = mockPrefs,
            useDynamicColors = true,
            idx = 0,
            isPrimary = false,
            isLight = false,
            sdkInt = Build.VERSION_CODES.S
        )

        assertEquals(0x445566, result)
    }

    @Test
    fun testResolveColor_DynamicOn_ButOldSdk() {
        // Fallback to idx=0 default
        val mockContext = mock<Context> {
            on { getColor(R.color.widget_text_light) } doReturn 0x556677
        }
        val mockPrefs = mock<SharedPreferences>()

        val result = ColorResolver.resolveColor(
            context = mockContext,
            prefs = mockPrefs,
            useDynamicColors = true, // but SDK is old
            idx = 0,
            isPrimary = true,
            isLight = true,
            sdkInt = Build.VERSION_CODES.R
        )

        assertEquals(0x556677, result)
    }

    @Test
    fun testResolveColor_DefaultIdx0_Primary_Light() {
        val mockContext = mock<Context> {
            on { getColor(R.color.widget_text_light) } doReturn 0x667788
        }
        val mockPrefs = mock<SharedPreferences>()

        val result = ColorResolver.resolveColor(
            context = mockContext,
            prefs = mockPrefs,
            useDynamicColors = false,
            idx = 0,
            isPrimary = true,
            isLight = true,
            sdkInt = Build.VERSION_CODES.S
        )

        assertEquals(0x667788, result)
    }

    @Test
    fun testResolveColor_DefaultIdx0_Primary_Dark() {
        val mockContext = mock<Context>()
        val mockPrefs = mock<SharedPreferences>()

        val result = ColorResolver.resolveColor(
            context = mockContext,
            prefs = mockPrefs,
            useDynamicColors = false,
            idx = 0,
            isPrimary = true,
            isLight = false,
            sdkInt = Build.VERSION_CODES.S
        )

        assertEquals(Color.WHITE, result)
    }

    @Test
    fun testResolveColor_DefaultIdx0_Secondary_Light() {
        val mockContext = mock<Context> {
            on { getColor(R.color.widget_text_secondary_light) } doReturn 0x778899
        }
        val mockPrefs = mock<SharedPreferences>()

        val result = ColorResolver.resolveColor(
            context = mockContext,
            prefs = mockPrefs,
            useDynamicColors = false,
            idx = 0,
            isPrimary = false,
            isLight = true,
            sdkInt = Build.VERSION_CODES.S
        )

        assertEquals(0x778899, result)
    }

    @Test
    fun testResolveColor_DefaultIdx0_Secondary_Dark() {
        val mockContext = mock<Context>()
        val mockPrefs = mock<SharedPreferences>()

        val result = ColorResolver.resolveColor(
            context = mockContext,
            prefs = mockPrefs,
            useDynamicColors = false,
            idx = 0,
            isPrimary = false,
            isLight = false,
            sdkInt = Build.VERSION_CODES.S
        )

        assertEquals(Color.parseColor("#CCFFFFFF"), result)
    }

    @Test
    fun testResolveColor_Idx1_SdkS() {
        val mockContext = mock<Context> {
            on { getColor(android.R.color.system_accent1_500) } doReturn 0x889900
        }
        val mockPrefs = mock<SharedPreferences>()

        val result = ColorResolver.resolveColor(
            context = mockContext,
            prefs = mockPrefs,
            useDynamicColors = false,
            idx = 1,
            isPrimary = true, // shouldn't matter for idx 1
            isLight = true,   // shouldn't matter for idx 1
            sdkInt = Build.VERSION_CODES.S
        )

        assertEquals(0x889900, result)
    }

    @Test
    fun testResolveColor_Idx1_OldSdk() {
        val mockContext = mock<Context>()
        val mockPrefs = mock<SharedPreferences>()

        val result = ColorResolver.resolveColor(
            context = mockContext,
            prefs = mockPrefs,
            useDynamicColors = false,
            idx = 1,
            isPrimary = true,
            isLight = true,
            sdkInt = Build.VERSION_CODES.R
        )

        assertEquals(Color.CYAN, result)
    }

    @Test
    fun testResolveColor_Idx2_CustomColor() {
        val mockContext = mock<Context>()
        val mockPrefs = mock<SharedPreferences> {
            on { getInt("text_color_primary_r", 255) } doReturn 100
            on { getInt("text_color_primary_g", 255) } doReturn 150
            on { getInt("text_color_primary_b", 255) } doReturn 200
        }

        val result = ColorResolver.resolveColor(
            context = mockContext,
            prefs = mockPrefs,
            useDynamicColors = false,
            idx = 2,
            isPrimary = true,
            isLight = true,
            sdkInt = Build.VERSION_CODES.S
        )

        assertEquals(Color.rgb(100, 150, 200), result)
    }

    @Test
    fun testResolveColor_ElseIdx_Primary() {
        val mockContext = mock<Context>()
        val mockPrefs = mock<SharedPreferences>()

        val result = ColorResolver.resolveColor(
            context = mockContext,
            prefs = mockPrefs,
            useDynamicColors = false,
            idx = 99,
            isPrimary = true,
            isLight = true,
            sdkInt = Build.VERSION_CODES.S
        )

        assertEquals(Color.WHITE, result)
    }

    @Test
    fun testResolveColor_ElseIdx_Secondary() {
        val mockContext = mock<Context>()
        val mockPrefs = mock<SharedPreferences>()

        val result = ColorResolver.resolveColor(
            context = mockContext,
            prefs = mockPrefs,
            useDynamicColors = false,
            idx = 99,
            isPrimary = false,
            isLight = true,
            sdkInt = Build.VERSION_CODES.S
        )

        assertEquals(Color.parseColor("#CCFFFFFF"), result)
    }
}
