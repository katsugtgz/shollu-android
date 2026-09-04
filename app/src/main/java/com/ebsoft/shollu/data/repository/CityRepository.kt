package com.ebsoft.shollu.data.repository

import android.content.Context
import com.ebsoft.shollu.R
import com.ebsoft.shollu.data.db.dao.CityDao
import com.ebsoft.shollu.data.db.entity.CityEntity
import com.ebsoft.shollu.data.model.City
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.InputStreamReader

class CityRepository(
    private val context: Context,
    private val cityDao: CityDao
) {
    val allCities: Flow<List<City>> = cityDao.getAllCities().map { list ->
        list.map { it.toModel() }
    }

    suspend fun getCityById(id: Long): City? {
        return cityDao.getCityById(id)?.toModel()
    }

    suspend fun initializeCitiesIfNeeded(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val count = cityDao.getCityCount()
            if (count == 0) {
                val cities: List<CityEntity> = try {
                    context.resources.openRawResource(R.raw.cities).use { inputStream ->
                        InputStreamReader(inputStream, Charsets.UTF_8).use { reader ->
                            val cityType = object : TypeToken<List<CityEntity>>() {}.type
                            val parsed: List<CityEntity>? = Gson().fromJson(reader, cityType)
                            if (parsed.isNullOrEmpty()) getFallbackCities() else parsed
                        }
                    }
                } catch (e: Exception) {
                    getFallbackCities()
                }
                if (cities.isNotEmpty()) {
                    cityDao.insertCities(cities)
                }
            }
        }
    }

    private fun getFallbackCities(): List<CityEntity> = listOf(
        CityEntity(name = "Jakarta", province = "DKI Jakarta", country = "Indonesia", latitude = -6.2088, longitude = 106.8456, elevation = 8.0, timezone = 7.0),
        CityEntity(name = "Surabaya", province = "Jawa Timur", country = "Indonesia", latitude = -7.2575, longitude = 112.7521, elevation = 5.0, timezone = 7.0),
        CityEntity(name = "Bandung", province = "Jawa Barat", country = "Indonesia", latitude = -6.9175, longitude = 107.6191, elevation = 768.0, timezone = 7.0),
        CityEntity(name = "Medan", province = "Sumatera Utara", country = "Indonesia", latitude = 3.5952, longitude = 98.6722, elevation = 26.0, timezone = 7.0),
        CityEntity(name = "Semarang", province = "Jawa Tengah", country = "Indonesia", latitude = -6.9667, longitude = 110.4167, elevation = 4.0, timezone = 7.0),
        CityEntity(name = "Makassar", province = "Sulawesi Selatan", country = "Indonesia", latitude = -5.1477, longitude = 119.4327, elevation = 2.0, timezone = 8.0),
        CityEntity(name = "Palembang", province = "Sumatera Selatan", country = "Indonesia", latitude = -2.9761, longitude = 104.7754, elevation = 8.0, timezone = 7.0),
        CityEntity(name = "Yogyakarta", province = "DI Yogyakarta", country = "Indonesia", latitude = -7.7956, longitude = 110.3695, elevation = 113.0, timezone = 7.0),
        CityEntity(name = "Denpasar", province = "Bali", country = "Indonesia", latitude = -8.6705, longitude = 115.2126, elevation = 4.0, timezone = 8.0),
        CityEntity(name = "Banda Aceh", province = "Aceh", country = "Indonesia", latitude = 5.5483, longitude = 95.3238, elevation = 21.0, timezone = 7.0),
        CityEntity(name = "Balikpapan", province = "Kalimantan Timur", country = "Indonesia", latitude = -1.2379, longitude = 116.8529, elevation = 10.0, timezone = 8.0),
        CityEntity(name = "Jayapura", province = "Papua", country = "Indonesia", latitude = -2.5337, longitude = 140.7181, elevation = 287.0, timezone = 9.0),
        CityEntity(name = "Makkah", province = "Makkah", country = "Saudi Arabia", latitude = 21.4225, longitude = 39.8262, elevation = 277.0, timezone = 3.0),
        CityEntity(name = "Madinah", province = "Madinah", country = "Saudi Arabia", latitude = 24.5247, longitude = 39.5692, elevation = 608.0, timezone = 3.0)
    )

    suspend fun insertCustomCity(city: City): Long {
        return cityDao.insertCity(CityEntity.fromModel(city))
    }
}
