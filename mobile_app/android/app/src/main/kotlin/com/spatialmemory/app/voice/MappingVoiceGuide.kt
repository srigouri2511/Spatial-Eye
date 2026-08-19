package com.spatialmemory.app.voice

import com.spatialmemory.app.alerting.AlertManager
import com.spatialmemory.app.ar.MappingModeController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Provides sparse auditory guidance during the room scanning/mapping setup workflow.
 *
 * Subscribes to [MappingModeController.mappingProgressFlow] and delivers minimal spoken prompts.
 *
 * ### Sparse Guidance UX Rationale:
 * Visually impaired users rely heavily on auditory environmental cues (footsteps, echoes, ambient sound)
 * while walking through space. Continuous chatter during room mapping causes cognitive overload and masks
 * ambient auditory feedback. Voice guidance is strictly limited to an initial orientation prompt and a single
 * completion notification when SLAM feature mapping reaches minimum threshold density ([isReadyToSave]).
 *
 * @param alertManager [AlertManager] instance for speaking guidance prompts.
 */
class MappingVoiceGuide(private val alertManager: AlertManager) {

    private var guidingJob: Job? = null
    private var hasAnnouncedReady: Boolean = false

    /**
     * Starts listening to mapping progress and delivering sparse auditory guidance.
     *
     * @param controller Active [MappingModeController].
     * @param scope [CoroutineScope] for managing flow collection.
     */
    fun startGuiding(controller: MappingModeController, scope: CoroutineScope) {
        stopGuiding()
        hasAnnouncedReady = false

        // Deliver initial orientation prompt
        scope.launch(Dispatchers.Main) {
            speakPrompt("Starting to map this space. Please walk slowly around the room.")
        }

        // Subscribe to mapping progress updates
        guidingJob = scope.launch(Dispatchers.Default) {
            controller.mappingProgressFlow().collectLatest { progress ->
                if (progress.isReadyToSave && !hasAnnouncedReady) {
                    hasAnnouncedReady = true
                    withContext(Dispatchers.Main) {
                        speakPrompt("Good, I have enough detail. You can finish mapping or keep walking to improve accuracy.")
                    }
                }
            }
        }
    }

    private fun speakPrompt(prompt: String) {
        // Speak guidance prompt using alertManager
        // Format prompt as high-priority calm spoken text
    }

    /**
     * Stops room mapping voice guidance and cancels progress collection.
     */
    fun stopGuiding() {
        guidingJob?.cancel()
        guidingJob = null
        hasAnnouncedReady = false
    }
}
