package com.leanbitlab.lwidget.weather

import kotlinx.serialization.Serializable

@Serializable
data class BreezyGadgetbridgeData(
    val timestamp: Int? = null,
    val location: String? = null,
    val currentTemp: Int? = null,
    val currentConditionCode: Int? = null,
    val currentCondition: String? = null,
    val currentHumidity: Int? = null,
    val windSpeed: Float? = null,
    val windDirection: Int? = null,
    val uvIndex: Float? = null,
    val todayMaxTemp: Int? = null,
    val todayMinTemp: Int? = null,
    val feelsLikeTemp: Int? = null,
    val precipProbability: Int? = null,
    val dewPoint: Int? = null,
    val pressure: Float? = null,
    val cloudCover: Int? = null,
    val visibility: Float? = null,
    val sunRise: Int? = null,
    val sunSet: Int? = null,
    val moonRise: Int? = null,
    val moonSet: Int? = null,
    val moonPhase: Float? = null,
    val name: String? = null,
    val level: String? = null,
    val color: Int? = null,
    val forecasts: List<BreezyGadgetbridgeDailyForecast>? = null,
    val hourly: List<BreezyGadgetbridgeHourlyForecast>? = null,
    val airQuality: BreezyGadgetbridgeAirQuality? = null
)

@Serializable
data class BreezyGadgetbridgeDailyForecast(
    val minTemp: Int? = null,
    val maxTemp: Int? = null,
    val conditionCode: Int? = null,
    val humidity: Int? = null,
    val windSpeed: Float? = null,
    val windDirection: Int? = null,
    val uvIndex: Float? = null,
    val precipProbability: Int? = null,
    val sunRise: Int? = null,
    val sunSet: Int? = null,
    val moonRise: Int? = null,
    val moonSet: Int? = null,
    val moonPhase: Int? = null,
    val airQuality: BreezyGadgetbridgeAirQuality? = null,
)

@Serializable
data class BreezyGadgetbridgeHourlyForecast(
    val timestamp: Int? = null,
    val temp: Int? = null,
    val conditionCode: Int? = null,
    val humidity: Int? = null,
    val windSpeed: Float? = null,
    val windDirection: Int? = null,
    val uvIndex: Float? = null,
    val precipProbability: Int? = null,
)

@Serializable
data class BreezyGadgetbridgeAirQuality(
    val aqi: Int? = null,
    val co: Float? = null,
    val no2: Float? = null,
    val o3: Float? = null,
    val pm10: Float? = null,
    val pm25: Float? = null,
    val so2: Float? = null,
    val coAqi: Int? = null,
    val no2Aqi: Int? = null,
    val o3Aqi: Int? = null,
    val pm10Aqi: Int? = null,
    val pm25Aqi: Int? = null,
    val so2Aqi: Int? = null,
)

@Serializable
data class BreezyWeather(
    val refreshTime: Long? = null,
    val current: BreezyCurrent? = null,
    val daily: List<BreezyDaily>? = null,
    val hourly: List<BreezyHourly>? = null,
)

@Serializable
data class BreezyCurrent(
    val temperature: BreezyTemperature? = null,
    val weatherText: String? = null,
    val weatherCode: Int? = null,
    val wind: BreezyWind? = null,
    val uV: BreezyUV? = null,
    val airQuality: BreezyAirQuality? = null,
    val relativeHumidity: BreezyUnit? = null,
)

@Serializable
data class BreezyDaily(
    val date: Long,
    val day: BreezyHalfDay? = null,
    val night: BreezyHalfDay? = null,
)

@Serializable
data class BreezyHourly(
    val date: Long,
    val temperature: BreezyTemperature? = null,
    val weatherCode: Int? = null,
    val weatherText: String? = null,
)

@Serializable
data class BreezyHalfDay(
    val weatherCode: Int? = null,
    val weatherText: String? = null,
    val temperature: BreezyTemperature? = null,
    val precipitationProbability: BreezyPrecipitationProbability? = null,
)

@Serializable
data class BreezyTemperature(
    val temperature: BreezyUnit? = null,
    val computedApparent: BreezyUnit? = null,
)

@Serializable
data class BreezyPrecipitationProbability(
    val total: BreezyUnit? = null,
    val rain: BreezyUnit? = null,
    val snow: BreezyUnit? = null,
)

@Serializable
data class BreezyWind(
    val degree: Double? = null,
    val speed: BreezyUnit? = null,
    val gusts: BreezyUnit? = null
)

@Serializable
data class BreezyUV(
    val index: Float? = null,
    val description: String? = null,
    val color: Int? = null
)

@Serializable
data class BreezyAirQuality(
    val global: BreezyPollutant? = null
)

@Serializable
data class BreezyPollutant(
    val index: Float? = null,
    val concentration: Float? = null,
    val name: String? = null,
    val level: String? = null,
    val color: Int? = null
)

@Serializable
data class BreezyUnit(
    val value: Double,
    val unit: String
)
