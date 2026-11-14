package com.leanbitlab.lwidget

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsLayoutTest {

    @Test
    fun noInsetWhenContentFits() {
        assertEquals(0, contentHorizontalInset(availablePx = 1080, maxContentPx = 1800))
        assertEquals(0, contentHorizontalInset(availablePx = 1800, maxContentPx = 1800))
    }

    @Test
    fun insetCentersContentWhenWider() {
        // 2400 wide, 1800 max -> 300px each side
        assertEquals(300, contentHorizontalInset(availablePx = 2400, maxContentPx = 1800))
    }

    @Test
    fun oddRemainderIsFloored() {
        assertEquals(150, contentHorizontalInset(availablePx = 2101, maxContentPx = 1800))
    }
}
