package com.leanbitlab.lwidget.weather

import android.content.Context
import kotlinx.serialization.json.Json

object BreezyWeatherFetcher {

    private const val PREFS_NAME = "lwidget_breezy_weather_data"
    private const val KEY_WEATHER_JSON = "weather_json"

    /**
     * Saves the weather JSON string received from the Breezy Weather broadcast.
     */
    fun saveLatestWeatherData(context: Context, weatherJson: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_WEATHER_JSON, weatherJson)
            .apply()
    }

    /**
     * Reads the cached weather data from SharedPreferences.
     */
    fun fetchLocalWeather(context: Context): BreezyGadgetbridgeData? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_WEATHER_JSON, null)
        
        if (jsonString.isNullOrEmpty()) return null
        
        return try {
            val json = Json { ignoreUnknownKeys = true }
            json.decodeFromString<BreezyGadgetbridgeData>(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
