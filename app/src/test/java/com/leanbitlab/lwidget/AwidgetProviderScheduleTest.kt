package com.leanbitlab.lwidget

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AwidgetProviderScheduleTest {

    private lateinit var context: Context
    private lateinit var alarmManager: AlarmManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        context.getSharedPreferences("com.leanbitlab.lwidget.PREFS", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun setIntervalMinutes(minutes: Float) {
        context.getSharedPreferences("com.leanbitlab.lwidget.PREFS", Context.MODE_PRIVATE)
            .edit().putFloat("update_interval", minutes).commit()
    }

    @Test
    fun onEnabledSchedulesDefaultFifteenMinuteInterval() {
        AwidgetProvider().onEnabled(context)

        val alarm = shadowOf(alarmManager).nextScheduledAlarm
        assertNotNull(alarm)
        assertEquals(15 * 60 * 1000L, alarm!!.interval)
    }

    @Test
    fun intervalUsesConfiguredValue() {
        setIntervalMinutes(30f)

        AwidgetProvider().onEnabled(context)

        assertEquals(30 * 60 * 1000L, shadowOf(alarmManager).nextScheduledAlarm!!.interval)
    }

    @Test
    fun intervalIsNeverBelowOneMinute() {
        setIntervalMinutes(0.1f)

        AwidgetProvider().onEnabled(context)

        assertEquals(60_000L, shadowOf(alarmManager).nextScheduledAlarm!!.interval)
    }

    @Test
    fun onDisabledCancelsTheAlarm() {
        val provider = AwidgetProvider()
        provider.onEnabled(context)
        assertNotNull(shadowOf(alarmManager).nextScheduledAlarm)

        provider.onDisabled(context)

        assertNull(shadowOf(alarmManager).nextScheduledAlarm)
    }
}
