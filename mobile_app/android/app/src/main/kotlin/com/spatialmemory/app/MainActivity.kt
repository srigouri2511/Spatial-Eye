package com.spatialmemory.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.ar.core.Session
import com.spatialmemory.app.alerting.AlertCoordinator
import com.spatialmemory.app.alerting.AlertManager
import com.spatialmemory.app.ar.ArAvailability
import com.spatialmemory.app.ar.ArSessionManager
import com.spatialmemory.app.ar.MappingModeController
import com.spatialmemory.app.core.CameraLoopController
import com.spatialmemory.app.data.Place
import com.spatialmemory.app.data.SpatialMemoryDatabase
import com.spatialmemory.app.detection.DetectionPipeline
import com.spatialmemory.app.diffing.ChangeDetectionEngine
import com.spatialmemory.app.voice.MappingTrigger
import com.spatialmemory.app.voice.MappingVoiceGuide
import com.spatialmemory.app.voice.QueryResponder
import com.spatialmemory.app.voice.VoiceQueryManager
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugins.GeneratedPluginRegistrant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Main Activity driving the end-to-end Spatial Memory Assistant experience.
 *
 * Extends [FlutterActivity] for Android v2 embedding compatibility.
 */
class MainActivity : FlutterActivity(), MappingTrigger {

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        GeneratedPluginRegistrant.registerWith(flutterEngine)
    }

    private lateinit var database: SpatialMemoryDatabase
    private lateinit var arSessionManager: ArSessionManager
    private lateinit var detectionPipeline: DetectionPipeline
    private lateinit var diffingEngine: ChangeDetectionEngine
    private lateinit var alertManager: AlertManager
    private lateinit var alertCoordinator: AlertCoordinator
    private lateinit var voiceQueryManager: VoiceQueryManager
    private lateinit var queryResponder: QueryResponder
    private lateinit var mappingModeController: MappingModeController
    private lateinit var mappingVoiceGuide: MappingVoiceGuide
    private lateinit var cameraLoopController: CameraLoopController

    private var arSession: Session? = null
    private var currentPlace: Place? = null
    private var isMappingMode: Boolean = false

    private lateinit var tvCurrentPlace: TextView
    private lateinit var btnSelectPlace: Button
    private lateinit var btnVoiceQuery: Button
    private lateinit var btnScanRoom: Button

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        if (cameraGranted) {
            setupArSession()
        } else {
            // Speak permission warning via TTS
            lifecycleScope.launch {
                alertManager.handleChanges(emptyList(), floatArrayOf(0f, 0f, 0f), 0f)
            }
        }
    }

    private val selectPlaceLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val placeId = result.data?.getStringExtra(PlaceSelectionActivity.EXTRA_SELECTED_PLACE_ID)
            val startMapping = result.data?.getBooleanExtra(PlaceSelectionActivity.EXTRA_START_MAPPING, false) ?: false

            if (startMapping) {
                startMapping(null)
            } else if (placeId != null) {
                loadSelectedPlace(placeId)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        database = (application as SpatialMemoryApp).database

        // Initialize subsystem components
        arSessionManager = ArSessionManager()
        detectionPipeline = DetectionPipeline(this)
        diffingEngine = ChangeDetectionEngine(database)
        alertManager = AlertManager(this, database)
        alertCoordinator = AlertCoordinator(this, database, alertManager)
        voiceQueryManager = VoiceQueryManager(this)
        queryResponder = QueryResponder(database)
        mappingModeController = MappingModeController(this, database)
        mappingVoiceGuide = MappingVoiceGuide(alertManager)

        cameraLoopController = CameraLoopController(
            context = this,
            database = database,
            arSessionManager = arSessionManager,
            detectionPipeline = detectionPipeline,
            diffingEngine = diffingEngine,
            alertCoordinator = alertCoordinator,
            alertManager = alertManager
        )

        tvCurrentPlace = findViewById(R.id.tvCurrentPlace)
        btnSelectPlace = findViewById(R.id.btnSelectPlace)
        btnVoiceQuery = findViewById(R.id.btnVoiceQuery)
        btnScanRoom = findViewById(R.id.btnScanRoom)

        btnSelectPlace.setOnClickListener {
            val intent = Intent(this, PlaceSelectionActivity::class.java)
            selectPlaceLauncher.launch(intent)
        }

        btnVoiceQuery.setOnClickListener {
            triggerVoiceQuery()
        }

        btnScanRoom.setOnClickListener {
            startMapping(null)
        }

        checkPermissionsAndSetup()
    }

    private fun checkPermissionsAndSetup() {
        val requiredPermissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            setupArSession()
        } else {
            requestPermissionsLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun setupArSession() {
        when (val availability = arSessionManager.checkArCoreAvailability(this)) {
            is ArAvailability.Available -> {
                val sessionResult = arSessionManager.createSession(this)
                if (sessionResult.isSuccess) {
                    arSession = sessionResult.getOrThrow()
                    resumeArSession()
                }
            }

            is ArAvailability.NeedsInstall -> {
                // ARCore installation flow will be triggered by createSession
            }

            is ArAvailability.Unsupported -> {
                // Gracefully handle unsupported device
            }
        }
    }

    private fun resumeArSession() {
        val session = arSession ?: return
        val resumeResult = arSessionManager.resumeSession(session)
        if (resumeResult.isSuccess) {
            cameraLoopController.startLoop(session, lifecycleScope)
        }
    }

    private fun loadSelectedPlace(placeId: String) {
        lifecycleScope.launch {
            val place = database.placeDao().getPlaceById(placeId)
            if (place != null) {
                currentPlace = place
                cameraLoopController.currentPlace = place
                tvCurrentPlace.text = "Current Place: ${place.displayName}"

                val session = arSession
                if (session != null) {
                    // Re-localize session against place's saved map
                    val loadResult = arSessionManager.let {
                        com.spatialmemory.app.ar.WorldMapPersistence().loadWorldMap(this@MainActivity, session, place)
                    }
                }
            }
        }
    }

    override fun startMapping(placeName: String?) {
        val name = placeName ?: "Room_${System.currentTimeMillis() % 10000}"
        val placeId = name.lowercase().replace(" ", "_")

        isMappingMode = true
        mappingModeController.startMapping(placeId, name)
        mappingVoiceGuide.startGuiding(mappingModeController, lifecycleScope)

        tvCurrentPlace.text = "Mapping Mode: $name"
    }

    private fun triggerVoiceQuery() {
        lifecycleScope.launch {
            val utterance = voiceQueryManager.listenForCommand(timeoutMs = 6000L)
            if (utterance != null) {
                val intent = voiceQueryManager.classifyIntent(utterance)
                queryResponder.respond(intent, currentPlace, alertManager, this@MainActivity)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (arSession != null) {
            resumeArSession()
        }
    }

    override fun onPause() {
        super.onPause()
        cameraLoopController.pauseLoop()
        arSession?.let { arSessionManager.pauseSession(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraLoopController.stopLoop()
        arSession?.let { arSessionManager.closeSession(it) }
        alertCoordinator.release()
        voiceQueryManager.release()
        detectionPipeline.close()
    }
}
