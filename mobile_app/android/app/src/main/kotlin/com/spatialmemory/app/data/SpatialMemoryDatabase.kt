package com.spatialmemory.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Main Room Database for the Spatial Memory Assistant app.
 *
 * Persists localized 3D place maps, landmarks, habitual walking corridors, and spatial change logs.
 * Provides thread-safe singleton access using double-checked locking.
 */
@Database(
    entities = [
        Place::class,
        Landmark::class,
        WalkCorridor::class,
        ChangeEvent::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SpatialMemoryDatabase : RoomDatabase() {

    /** Accessor for [PlaceDao]. */
    abstract fun placeDao(): PlaceDao

    /** Accessor for [LandmarkDao]. */
    abstract fun landmarkDao(): LandmarkDao

    /** Accessor for [WalkCorridorDao]. */
    abstract fun walkCorridorDao(): WalkCorridorDao

    /** Accessor for [ChangeEventDao]. */
    abstract fun changeEventDao(): ChangeEventDao

    companion object {
        @Volatile
        private var INSTANCE: SpatialMemoryDatabase? = null

        /**
         * Returns the singleton instance of [SpatialMemoryDatabase], instantiating it if necessary
         * using double-checked locking for thread safety.
         *
         * @param context Application context.
         * @return Singleton [SpatialMemoryDatabase] instance.
         */
        fun getInstance(context: Context): SpatialMemoryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SpatialMemoryDatabase::class.java,
                    "spatial_memory_database"
                ).build().also { INSTANCE = it }
                instance
            }
        }
    }
}
