package com.spatialmemory.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.spatialmemory.app.data.Place
import com.spatialmemory.app.data.SpatialMemoryDatabase
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Activity for selecting an active mapped [Place] or initiating a new room scan.
 *
 * Provides high-contrast, accessible touch targets for visually impaired users.
 */
class PlaceSelectionActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SELECTED_PLACE_ID = "extra_selected_place_id"
        const val EXTRA_START_MAPPING = "extra_start_mapping"
    }

    private lateinit var database: SpatialMemoryDatabase
    private lateinit var listView: ListView
    private lateinit var btnScanNewRoom: Button
    private val placesList = mutableListOf<Place>()
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_place_selection)

        database = (application as SpatialMemoryApp).database

        listView = findViewById(R.id.listViewPlaces)
        btnScanNewRoom = findViewById(R.id.btnScanNewRoom)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            if (position in placesList.indices) {
                val selectedPlace = placesList[position]
                val intent = Intent().apply {
                    putExtra(EXTRA_SELECTED_PLACE_ID, selectedPlace.id)
                }
                setResult(Activity.RESULT_OK, intent)
                finish()
            }
        }

        btnScanNewRoom.setOnClickListener {
            val intent = Intent().apply {
                putExtra(EXTRA_START_MAPPING, true)
            }
            setResult(Activity.RESULT_OK, intent)
            finish()
        }

        observePlaces()
    }

    private fun observePlaces() {
        lifecycleScope.launch {
            database.placeDao().getAllPlaces().collectLatest { places ->
                placesList.clear()
                placesList.addAll(places)

                val displayNames = places.map { "${it.displayName} (Updated ${formatTime(it.lastUpdated)})" }
                adapter.clear()
                adapter.addAll(displayNames)
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun formatTime(timestamp: Long): String {
        val diff = (System.currentTimeMillis() - timestamp) / (1000 * 60)
        return if (diff < 60) "${diff}m ago" else "${diff / 60}h ago"
    }
}
