package com.leanbitlab.lwidget.weather

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class BreezyWeatherFetcherTest {

    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences

    private val prefsName = "lwidget_breezy_weather_data"
    private val keyWeatherJson = "weather_json"

    private lateinit var mockEditor: SharedPreferences.Editor

    @Before
    fun setup() {
        mockEditor = mock {
            on { putString(org.mockito.kotlin.any(), org.mockito.kotlin.any()) } doReturn it
        }
        mockPrefs = mock {
            on { edit() } doReturn mockEditor
        }
        mockContext = mock {
            on { getSharedPreferences(prefsName, Context.MODE_PRIVATE) } doReturn mockPrefs
        }
    }

    @Test
    fun `saveLatestWeatherData saves json string to preferences`() {
        val validJson = """{"temp": 20}"""

        BreezyWeatherFetcher.saveLatestWeatherData(mockContext, validJson)

        org.mockito.kotlin.verify(mockEditor).putString(keyWeatherJson, validJson)
        org.mockito.kotlin.verify(mockEditor).apply()
    }

    @Test
    fun `saveLatestWeatherData with empty string saves empty string`() {
        BreezyWeatherFetcher.saveLatestWeatherData(mockContext, "")

        org.mockito.kotlin.verify(mockEditor).putString(keyWeatherJson, "")
        org.mockito.kotlin.verify(mockEditor).apply()
    }

    @Test
    fun `fetchLocalWeather with valid json returns parsed data`() {
        val validJson = """
            {
                "timestamp": 1678886400,
                "location": "Berlin",
                "currentTemp": 15
            }
        """.trimIndent()

        whenever(mockPrefs.getString(keyWeatherJson, null)).thenReturn(validJson)

        val result = BreezyWeatherFetcher.fetchLocalWeather(mockContext)

        assertEquals(1678886400, result?.timestamp)
        assertEquals("Berlin", result?.location)
        assertEquals(15, result?.currentTemp)
    }

    @Test
    fun `fetchLocalWeather with null json returns null`() {
        whenever(mockPrefs.getString(keyWeatherJson, null)).thenReturn(null)

        val result = BreezyWeatherFetcher.fetchLocalWeather(mockContext)

        assertNull(result)
    }

    @Test
    fun `fetchLocalWeather with empty json returns null`() {
        whenever(mockPrefs.getString(keyWeatherJson, null)).thenReturn("")

        val result = BreezyWeatherFetcher.fetchLocalWeather(mockContext)

        assertNull(result)
    }

    @Test
    fun `fetchLocalWeather with malformed json returns null`() {
        val malformedJson = """{ "timestamp": 1678886400, "location": """

        whenever(mockPrefs.getString(keyWeatherJson, null)).thenReturn(malformedJson)

        val result = BreezyWeatherFetcher.fetchLocalWeather(mockContext)

        assertNull(result)
    }
}
