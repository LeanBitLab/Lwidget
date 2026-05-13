package com.leanbitlab.lwidget

import android.content.Context
import android.content.SharedPreferences
import android.hardware.SensorEvent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StepCounterServiceTest {

    private lateinit var service: StepCounterService
    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        prefs = context.getSharedPreferences("com.leanbitlab.lwidget.PREFS", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        // Create service but don't call onCreate yet so tests can set initial SharedPreferences
    }

    private fun startService() {
        service = Robolectric.buildService(StepCounterService::class.java).create().get()
    }

    private fun createMockSensorEvent(steps: Float): SensorEvent {
        val constructor = SensorEvent::class.java.declaredConstructors.first { it.parameterCount == 1 }
        constructor.isAccessible = true
        val event = constructor.newInstance(1) as SensorEvent
        val valuesField = android.hardware.SensorEvent::class.java.getField("values")
        valuesField.isAccessible = true
        val values = FloatArray(1)
        values[0] = steps
        valuesField.set(event, values)
        return event
    }

    @Test
    fun testOnSensorChanged_hardwareRebooted() {
        // Setup initial state: previous total was 1000, baseline was 200
        // Meaning the user took 800 steps (1000 - 200)
        prefs.edit()
            .putFloat("last_total_steps", 1000f)
            .putFloat("step_baseline", 200f)
            .putString("step_date", LocalDate.now().toString())
            .apply()

        // Hardware rebooted, now sensor says 50 steps
        startService()
        val event = createMockSensorEvent(50f)
        service.onSensorChanged(event)

        // New baseline should be: 50 - (1000 - 200) = 50 - 800 = -750
        assertEquals(-750f, prefs.getFloat("step_baseline", 0f), 0.001f)
        assertEquals(50f, prefs.getFloat("last_total_steps", 0f), 0.001f)
    }

    @Test
    fun testOnSensorChanged_hardwareRebootedMultipleTimes() {
        // Initial state
        prefs.edit()
            .putFloat("last_total_steps", 1000f)
            .putFloat("step_baseline", 200f)
            .putString("step_date", LocalDate.now().toString())
            .apply()

        // First reboot, sensor goes from 1000 -> 50
        startService()
        service.onSensorChanged(createMockSensorEvent(50f))

        // Expected: baseline = 50 - (1000 - 200) = -750
        assertEquals(-750f, prefs.getFloat("step_baseline", 0f), 0.001f)
        assertEquals(50f, prefs.getFloat("last_total_steps", 0f), 0.001f)

        // Steps increase from 50 to 150
        service.onSensorChanged(createMockSensorEvent(150f))

        // Expected: baseline remains -750, last_total_steps = 150
        assertEquals(-750f, prefs.getFloat("step_baseline", 0f), 0.001f)
        assertEquals(150f, prefs.getFloat("last_total_steps", 0f), 0.001f)

        // Second reboot, sensor goes from 150 -> 20
        service.onSensorChanged(createMockSensorEvent(20f))

        // Expected: baseline = 20 - (150 - (-750)) = 20 - 900 = -880
        assertEquals(-880f, prefs.getFloat("step_baseline", 0f), 0.001f)
        assertEquals(20f, prefs.getFloat("last_total_steps", 0f), 0.001f)
    }

    @Test
    fun testOnSensorChanged_dailyReset() {
        // Setup state for yesterday
        val yesterday = LocalDate.now().minusDays(1).toString()
        prefs.edit()
            .putString("step_date", yesterday)
            .putFloat("last_total_steps", 500f)
            .putFloat("step_baseline", 100f)
            .apply()

        startService()
        val event = createMockSensorEvent(600f)
        service.onSensorChanged(event)

        // Expected: Should update date to today and set baseline to current total
        assertEquals(LocalDate.now().toString(), prefs.getString("step_date", ""))
        assertEquals(600f, prefs.getFloat("step_baseline", 0f), 0.001f)
        assertEquals(600f, prefs.getFloat("last_total_steps", 0f), 0.001f)
    }

    @Test
    fun testOnSensorChanged_normalStepIncrease() {
        // Normal state today
        prefs.edit()
            .putString("step_date", LocalDate.now().toString())
            .putFloat("last_total_steps", 1000f)
            .putFloat("step_baseline", 200f)
            .apply()

        // Step increases to 1050
        startService()
        val event = createMockSensorEvent(1050f)
        service.onSensorChanged(event)

        // Baseline shouldn't change
        assertEquals(200f, prefs.getFloat("step_baseline", 0f), 0.001f)
        assertEquals(1050f, prefs.getFloat("last_total_steps", 0f), 0.001f)
    }

    @Test
    fun testOnSensorChanged_nullEvent() {
        // Setup initial state
        prefs.edit()
            .putString("step_date", LocalDate.now().toString())
            .putFloat("last_total_steps", 1000f)
            .putFloat("step_baseline", 200f)
            .putBoolean("was_called", false) // marker to check if prefs was edited
            .apply()

        startService()
        service.onSensorChanged(null)

        // Ensure nothing was updated or changed
        assertEquals(1000f, prefs.getFloat("last_total_steps", 0f), 0.001f)
        assertEquals(200f, prefs.getFloat("step_baseline", 0f), 0.001f)
        assertFalse(prefs.getBoolean("was_called", true))
    }
}
