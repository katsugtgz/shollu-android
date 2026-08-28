package com.ebsoft.shollu.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ebsoft.shollu.data.model.City

@Entity(tableName = "cities")
data class CityEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val province: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val elevation: Double = 0.0,
    val timezone: Double = 7.0
) {
    fun toModel(): City = City(
        id = id,
        name = name,
        province = province,
        country = country,
        latitude = latitude,
        longitude = longitude,
        elevation = elevation,
        timezone = timezone
    )

    companion object {
        fun fromModel(model: City): CityEntity = CityEntity(
            id = model.id,
            name = model.name,
            province = model.province,
            country = model.country,
            latitude = model.latitude,
            longitude = model.longitude,
            elevation = model.elevation,
            timezone = model.timezone
        )
    }
}
