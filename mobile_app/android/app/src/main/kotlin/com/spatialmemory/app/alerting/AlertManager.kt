package com.spatialmemory.app.alerting

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import com.spatialmemory.app.data.SpatialMemoryDatabase
import com.spatialmemory.app.diffing.AlertThresholds
import com.spatialmemory.app.diffing.DetectedChange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.atan2

/**
 * 2D relative direction of a spatial change relative to the user's current facing heading.
 */
enum class Direction {
    AHEAD,
    SLIGHTLY_LEFT,
    SLIGHTLY_RIGHT,
    LEFT,
    RIGHT,
    BEHIND
}

/**
 * Manages multimodal alerts (haptic vibrations, stereo-panned audio cues, and TTS speech) for visually impaired users.
 *
 * Configures tiered alert delivery based on hazard severity:
 * - **Silent** (`< 0.35`): Logged quietly to memory database without user disruption.
 * - **Low / Haptic** (`0.35 <= score < 0.65`): Single short haptic pulse (~80ms) + stereo panned audio cue.
 * - **High / Spoken** (`>= 0.65`): Pre-alert haptic pulse (~200ms) + stereo panned audio cue + calm spoken TTS description.
 *
 * @param context Application context.
 * @param database [SpatialMemoryDatabase] instance for logging change events.
 */
class AlertManager(
    private val context: Context,
    private val database: SpatialMemoryDatabase
) : AutoCloseable {

    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false
    private var currentLocale: Locale = Locale.US

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private var soundPool: SoundPool? = null
    private var toneSoundId: Int = 0

    init {
        initTts()
        initSoundPool()
    }

    private fun initTts() {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = currentLocale
                isTtsInitialized = true
            }
        }
    }

    private fun initSoundPool() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(audioAttributes)
            .build()

        // Load short audio cue tone
        // TODO: In production release, load from res/raw/cue_tone.wav asset
        toneSoundId = 1
    }

    /**
     * Sets the language locale for Text-To-Speech engine delivery.
     */
    fun setLocale(locale: Locale) {
        currentLocale = locale
        if (isTtsInitialized) {
            tts?.language = locale
        }
    }

    /**
     * Calculates the 2D relative direction of a target position relative to the user's facing heading.
     *
     * @param changePosition Target object 3D position `[x, y, z]`.
     * @param userPosition User 3D position `[x, y, z]`.
     * @param userHeading User camera facing heading in radians.
     * @return [Direction] enum indicating relative direction.
     */
    fun relativeDirection(changePosition: FloatArray, userPosition: FloatArray, userHeading: Float): Direction {
        val dx = changePosition[0] - userPosition[0]
        val dz = changePosition[2] - userPosition[2]

        // Compute bearing angle in 2D floor plane (X, Z)
        val bearing = atan2(dx.toDouble(), -dz.toDouble()).toFloat()

        // Relative angle normalized to [-PI, PI]
        var relAngle = bearing - userHeading
        while (relAngle > Math.PI) relAngle -= (2 * Math.PI).toFloat()
        while (relAngle < -Math.PI) relAngle += (2 * Math.PI).toFloat()

        val degrees = Math.toDegrees(relAngle.toDouble()).toFloat()

        return when {
            degrees in -22.5f..22.5f -> Direction.AHEAD
            degrees in -67.5f..-22.5f -> Direction.SLIGHTLY_LEFT
            degrees in -112.5f..-67.5f -> Direction.LEFT
            degrees in 22.5f..67.5f -> Direction.SLIGHTLY_RIGHT
            degrees in 67.5f..112.5f -> Direction.RIGHT
            else -> Direction.BEHIND
        }
    }

    /**
     * Delivers spatial audio stereo panning based on target direction.
     *
     * ### Spatial Panning Note:
     * Adjusts left/right channel volume proportions (`leftVolume`, `rightVolume`) on [SoundPool].
     * Provides an intuitive directional audio cue without requiring specialized HRTF spatial audio hardware.
     */
    fun playStereoCue(direction: Direction) {
        val (leftVol, rightVol) = when (direction) {
            Direction.AHEAD -> Pair(0.8f, 0.8f)
            Direction.SLIGHTLY_LEFT -> Pair(0.9f, 0.5f)
            Direction.LEFT -> Pair(1.0f, 0.1f)
            Direction.SLIGHTLY_RIGHT -> Pair(0.5f, 0.9f)
            Direction.RIGHT -> Pair(0.1f, 1.0f)
            Direction.BEHIND -> Pair(0.5f, 0.5f)
        }
        soundPool?.play(toneSoundId, leftVol, rightVol, 1, 0, 1.0f)
    }

    /**
     * Processes detected spatial changes, delivering tiered haptic, audio, and TTS alerts.
     *
     * @param changes Scored changes from diffing engine.
     * @param userPosition User 3D camera position `[x, y, z]`.
     * @param userHeading User camera facing heading in radians.
     * @return List of [DetectedChange] items that triggered active user alerts.
     */
    suspend fun handleChanges(
        changes: List<DetectedChange>,
        userPosition: FloatArray,
        userHeading: Float
    ): List<DetectedChange> = withContext(Dispatchers.Main) {
        val alertedChanges = mutableListOf<DetectedChange>()
        val sortedChanges = changes.sortedByDescending { it.severityScore }

        for (change in sortedChanges) {
            val position = change.liveDetection?.worldPosition
                ?: change.landmark?.let { floatArrayOf(it.positionX, it.positionY, it.positionZ) }
                ?: continue

            val direction = relativeDirection(position, userPosition, userHeading)

            when {
                // Tier 1: Silent (< 0.35) -> Logged quietly to database (already handled in diffing engine)
                change.severityScore < AlertThresholds.SEVERITY_SILENT_MAX -> {
                    // Silent tier log
                }

                // Tier 2: Low / Haptic (0.35 <= score < 0.65) -> Single haptic pulse + stereo audio cue
                change.severityScore < AlertThresholds.SEVERITY_LOW_MAX -> {
                    triggerHapticPulse(durationMs = 80L)
                    playStereoCue(direction)
                    alertedChanges.add(change)
                }

                // Tier 3: High / Spoken (>= 0.65) -> Pre-alert haptic + stereo cue + calm spoken description
                else -> {
                    // Pre-alert haptic pulse (~200ms) gives the user a tactile heads-up before voice starts
                    triggerHapticPulse(durationMs = 200L)
                    playStereoCue(direction)

                    val directionPhrase = directionText(direction)
                    val spokenText = "${change.explanation} $directionPhrase."
                    speakCalm(spokenText)

                    alertedChanges.add(change)
                }
            }
        }

        alertedChanges
    }

    private fun triggerHapticPulse(durationMs: Long) {
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    }

    private fun speakCalm(text: String) {
        if (isTtsInitialized) {
            // Calm phrasing without exclamation marks ensures informative, non-panicked delivery
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "spatial_memory_alert_${System.currentTimeMillis()}")
        }
    }

    private fun directionText(direction: Direction): String {
        return when (direction) {
            Direction.AHEAD -> "ahead"
            Direction.SLIGHTLY_LEFT -> "slightly to your left"
            Direction.SLIGHTLY_RIGHT -> "slightly to your right"
            Direction.LEFT -> "on your left"
            Direction.RIGHT -> "on your right"
            Direction.BEHIND -> "behind you"
        }
    }

    override fun release() {
        close()
    }

    override fun close() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isTtsInitialized = false

        soundPool?.release()
        soundPool = null
    }
}
