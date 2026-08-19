package com.spatialmemory.app

import android.app.Application
import com.spatialmemory.app.data.SpatialMemoryDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Application class for Spatial Memory Assistant.
 *
 * Maintains application-wide singletons including the Room [SpatialMemoryDatabase] instance
 * and a long-lived application [CoroutineScope] for asynchronous background tasks.
 */
class SpatialMemoryApp : Application() {

    /** Application-wide database instance. */
    val database: SpatialMemoryDatabase by lazy {
        SpatialMemoryDatabase.getInstance(this)
    }

    /** Application-wide coroutine scope tied to Application lifecycle. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Initialize app singletons on startup
        database
    }
}
