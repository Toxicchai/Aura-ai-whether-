package com.example.data.db

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "favorite_cities")
data class FavoriteCity(
    @PrimaryKey val id: Long, // Use open-meteo ID, or a custom hash for GPS locations
    val name: String,
    val country: String?,
    val admin1: String?,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface FavoriteCityDao {
    @Query("SELECT * FROM favorite_cities ORDER BY timestamp DESC")
    fun getAllFavoritesFlow(): Flow<List<FavoriteCity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(city: FavoriteCity)

    @Delete
    suspend fun deleteFavorite(city: FavoriteCity)

    @Query("DELETE FROM favorite_cities WHERE id = :cityId")
    suspend fun deleteFavoriteById(cityId: Long)

    @Query("SELECT EXISTS(SELECT * FROM favorite_cities WHERE id = :cityId)")
    suspend fun isFavorite(cityId: Long): Boolean
}

@Database(entities = [FavoriteCity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteCityDao(): FavoriteCityDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "aura_weather_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
