package com.ebsoft.shollu.data.db.dao

import androidx.room.*
import com.ebsoft.shollu.data.db.entity.CityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CityDao {
    @Query("SELECT * FROM cities ORDER BY country = 'Indonesia' DESC, name ASC")
    fun getAllCities(): Flow<List<CityEntity>>

    @Query("SELECT * FROM cities WHERE name LIKE '%' || :query || '%' OR province LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchCities(query: String): Flow<List<CityEntity>>

    @Query("SELECT * FROM cities WHERE id = :id LIMIT 1")
    suspend fun getCityById(id: Long): CityEntity?

    @Query("SELECT COUNT(*) FROM cities")
    suspend fun getCityCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCities(cities: List<CityEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCity(city: CityEntity): Long
}
