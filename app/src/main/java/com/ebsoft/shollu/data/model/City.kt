package com.ebsoft.shollu.data.model

data class City(
    val id: Long = 0,
    val name: String,
    val province: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val elevation: Double = 0.0,
    val timezone: Double = 7.0
)
