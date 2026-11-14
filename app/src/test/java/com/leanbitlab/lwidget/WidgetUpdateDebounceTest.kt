package com.leanbitlab.lwidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WidgetUpdateDebounceTest {

    @Before
    fun setUp() {
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("com.leanbitlab.lwidget.PREFS", Context.MODE_PRIVATE)
            .edit().clear().putBoolean("is_first_launch", false).commit()
    }

    private fun updateBroadcasts(): Int =
        shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
            .broadcastIntents.count { it.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE }

    @Test
    fun rapidSliderChangesProduceOneWidgetUpdate() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        val before = updateBroadcasts()

        repeat(20) { activity.scheduleWidgetUpdate() }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))

        assertEquals(1, updateBroadcasts() - before)
    }

    @Test
    fun pendingUpdateIsCancelledOnDestroy() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()
        val before = updateBroadcasts()

        controller.get().scheduleWidgetUpdate()
        controller.pause().stop().destroy()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))

        assertEquals(0, updateBroadcasts() - before)
    }
}
