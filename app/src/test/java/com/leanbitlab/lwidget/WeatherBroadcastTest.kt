package com.leanbitlab.lwidget

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.leanbitlab.lwidget.weather.BreezyWeatherFetcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Breezy Weather sends data only via the Gadgetbridge broadcast; the provider must store it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WeatherBroadcastTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun sendWeather(json: String?) {
        val intent = Intent(context, AwidgetProvider::class.java).apply {
            action = AwidgetProvider.ACTION_GENERIC_WEATHER
            if (json != null) putExtra(AwidgetProvider.EXTRA_WEATHER_JSON, json)
        }
        context.sendBroadcast(intent)
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    @Test
    fun weatherBroadcastIsStored() {
        sendWeather("""{"currentTemp":291,"currentCondition":"Cloudy"}""")
        val data = BreezyWeatherFetcher.fetchLocalWeather(context)
        assertEquals(291, data?.currentTemp)
        assertEquals("Cloudy", data?.currentCondition)
    }

    @Test
    fun broadcastWithoutPayloadKeepsNothing() {
        sendWeather(null)
        assertNull(BreezyWeatherFetcher.fetchLocalWeather(context))
    }
}
