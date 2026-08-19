package com.spatialmemory.app.ar

import android.app.Activity
import android.content.Context
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException

/**
 * Represents the device availability state for ARCore features.
 */
sealed class ArAvailability {
    /** ARCore is installed and supported on this device. */
    object Available : ArAvailability()

    /** ARCore needs installation or an update before creating a session. */
    data class NeedsInstall(val installRequested: Boolean) : ArAvailability()

    /** ARCore is unsupported on this hardware or OS version. */
    data class Unsupported(val reason: String) : ArAvailability()
}

/**
 * Manages the ARCore [Session] lifecycle, hardware availability verification, and session configuration.
 *
 * ### Critical ARCore Lifecycle Gotchas:
 * 1. **Thread Rules**: [Session] creation, [Session.resume], and [Session.pause] MUST be invoked on the
 *    main Android UI thread. Calling these on background worker threads can cause GL context mismatches or native crashes.
 * 2. **Camera Permissions**: Android `android.permission.CAMERA` MUST be requested and granted by the user
 *    *before* attempting [createSession]. Instantiating a session without camera permissions will fail immediately.
 * 3. **Camera Availability Exception**: [Session.resume] can throw [CameraNotAvailableException] if the camera resource
 *    is temporarily locked by another system service, background camera pipeline (e.g., CameraX/TFLite), or incoming call.
 *    The calling Activity must handle this exception gracefully by notifying the user or retrying resumption.
 */
class ArSessionManager {

    private var installRequested: Boolean = false

    /**
     * Verifies ARCore installation and compatibility status on the host Android device.
     *
     * @param context Application or Activity context.
     * @return [ArAvailability] indicating whether ARCore is ready, requires installation, or is unsupported.
     */
    fun checkArCoreAvailability(context: Context): ArAvailability {
        return when (ArCoreApk.getInstance().checkAvailability(context)) {
            ArCoreApk.Availability.SUPPORTED_INSTALLED -> ArAvailability.Available

            ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD,
            ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> ArAvailability.NeedsInstall(installRequested)

            ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE -> ArAvailability.Unsupported("Device is not capable of running ARCore SLAM mapping.")

            ArCoreApk.Availability.UNKNOWN_CHECKING,
            ArCoreApk.Availability.UNKNOWN_ERROR,
            ArCoreApk.Availability.UNKNOWN_TIMED_OUT -> ArAvailability.NeedsInstall(installRequested)
        }
    }

    /**
     * Prompts ARCore APK installation if required, or creates a new configured [Session].
     *
     * MUST be called on the Main UI thread after camera permissions have been verified.
     *
     * @param activity Host Activity context required for APK installation popups.
     * @return [Result] containing the instantiated [Session] or an exception failure.
     */
    fun createSession(activity: Activity): Result<Session> {
        try {
            // Step 1: Prompt for ARCore installation if needed
            val installStatus = ArCoreApk.getInstance().requestInstall(activity, !installRequested)
            if (installStatus == ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
                installRequested = true
                return Result.failure(IllegalStateException("ARCore installation requested. Await install completion."))
            }

            // Step 2: Instantiate ARCore Session
            val session = Session(activity.applicationContext)

            // Step 3: Configure session for indoor spatial memory scanning
            val config = Config(session).apply {
                focusMode = Config.FocusMode.AUTO
                planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                lightEstimationMode = Config.LightEstimationMode.DISABLED // Disabled to conserve GPU processing for object detection
            }
            session.configure(config)

            return Result.success(session)
        } catch (e: UnavailableArcoreNotInstalledException) {
            return Result.failure(e)
        } catch (e: UnavailableUserDeclinedInstallationException) {
            return Result.failure(e)
        } catch (e: UnavailableApkTooOldException) {
            return Result.failure(e)
        } catch (e: UnavailableSdkTooOldException) {
            return Result.failure(e)
        } catch (e: UnavailableDeviceNotCompatibleException) {
            return Result.failure(e)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    /**
     * Resumes the ARCore session. MUST be invoked in Activity `onResume()` on the Main UI thread.
     *
     * @param session Active ARCore [Session].
     * @return [Result.success] or [Result.failure] if [CameraNotAvailableException] occurs.
     */
    fun resumeSession(session: Session): Result<Unit> {
        return try {
            session.resume()
            Result.success(Unit)
        } catch (e: CameraNotAvailableException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Pauses the ARCore session. MUST be invoked in Activity `onPause()` on the Main UI thread.
     *
     * @param session Active ARCore [Session].
     */
    fun pauseSession(session: Session) {
        try {
            session.pause()
        } catch (e: Exception) {
            // Log/ignore pause exceptions on tear-down
        }
    }

    /**
     * Closes and releases native GL/camera resources bound to the ARCore session.
     *
     * @param session Active ARCore [Session].
     */
    fun closeSession(session: Session) {
        try {
            session.close()
        } catch (e: Exception) {
            // Ignore close exceptions
        }
    }

    /**
     * Retrieves the latest AR frame for the current render tick.
     *
     * @param session Active ARCore [Session].
     * @return Current [Frame], or null if tracking is uninitialized or frame update failed.
     */
    fun currentFrame(session: Session): Frame? {
        return try {
            session.update()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Helper to extract the 6DoF camera position and orientation pose from the current frame.
     *
     * @param frame Latest AR [Frame].
     * @return Camera 6DoF [Pose], or null if camera tracking is lost/stopped.
     */
    fun cameraPose(frame: Frame?): Pose? {
        if (frame == null) return null
        val camera = frame.camera
        return if (camera.trackingState == TrackingState.TRACKING) {
            camera.pose
        } else {
            null
        }
    }
}
