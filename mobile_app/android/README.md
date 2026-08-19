# Spatial Memory Assistant (Android)

**Spatial Memory Assistant** is an Android application (Kotlin) designed for visually impaired users. Instead of describing every visual detail continuously, it creates a personalized 3D spatial map of frequently visited places (home, office, shop) and alerts the user **only when something changes** — a moved chair, a new obstacle, or an altered entrance.

---

## 🏗️ Architecture & Modules

The application is structured into 7 decoupled Kotlin packages:

1. **`com.spatialmemory.app.data`**: Room local persistence layer (`Place`, `Landmark`, `WalkCorridor`, `ChangeEvent` entities, DAOs, TypeConverters, `SpatialMemoryDatabase` singleton).
2. **`com.spatialmemory.app.ar`**: ARCore SLAM session wrapper (`ArSessionManager`), persistent spatial map exporter/loader (`WorldMapPersistence`), and room scanning setup controller (`MappingModeController`).
3. **`com.spatialmemory.app.detection`**: TFLite object detection pipeline (`ObjectDetector`), YUV-to-RGB conversion (`FrameConverter`), 2D-to-3D floor-contact raycasting (`DetectionToWorldMapper`), and camera frame pipeline (`DetectionPipeline`).
4. **`com.spatialmemory.app.diffing`**: Core spatial change detection engine (`ChangeDetectionEngine`), hazard severity scoring algorithm (`computeSeverity`), alert thresholds (`AlertThresholds`), and personalization learning loop (`PersonalizationAdjuster`).
5. **`com.spatialmemory.app.alerting`**: Multimodal alert manager (`AlertManager`), directional stereo audio panning, haptic vibration waveforms, Text-To-Speech (TTS), voice feedback capture (`FeedbackListener`), and rate-limiting debouncing (`AlertCoordinator`).
6. **`com.spatialmemory.app.voice`**: Active voice query manager (`VoiceQueryManager`), intent classifier (`classifyIntent`), spoken delta query responder (`QueryResponder`), and sparse room mapping guide (`MappingVoiceGuide`).
7. **`com.spatialmemory.app.core` & `com.spatialmemory.app`**: Top-level application wiring (`SpatialMemoryApp`), background camera processing loop (`CameraLoopController`), `MainActivity`, `PlaceSelectionActivity`, and accessible high-contrast UI layouts.

---

## 📦 Prerequisites & Assets Setup

### 1. TFLite Quantized Model Setup (`yolov8n_int8.tflite`)

The object detection pipeline expects a INT8 quantized YOLOv8 nano TFLite model located at:
`app/src/main/assets/yolov8n_int8.tflite`

#### How to Export & Install Model:
Using Python and Ultralytics YOLOv8:

```bash
# 1. Install ultralytics
pip install ultralytics tensorflow

# 2. Export YOLOv8 nano model to INT8 TFLite format
python -c "from ultralytics import YOLO; model = YOLO('yolov8n.pt'); model.export(format='tflite', int8=True)"
```

Copy the generated `yolov8n_int8.tflite` file into the Android project assets directory:

```bash
mkdir -p mobile_app/android/app/src/main/assets
cp yolov8n_int8.tflite mobile_app/android/app/src/main/assets/yolov8n_int8.tflite
```

---

## 📱 Hardware Requirements & ARCore Compatibility

- **Android OS**: Android 8.0 (Oreo / API level 26) or higher. Target SDK: 34.
- **Hardware Features**: Camera, Gyroscope, Accelerometer, and Microphone.
- **ARCore Support**: Target device MUST be certified on Google's ARCore Supported Devices List ([Google ARCore Supported Devices](https://developers.google.com/ar/devices)).
- **Depth API (Optional)**: Devices equipped with ToF (Time-of-Flight) sensors or LiDAR hardware provide refined pixel-accurate depth maps; devices without ToF gracefully fall back to AR plane hit testing.

---

## 🚀 Building & Running

### 1. Build Debug APK via Gradle

From the `mobile_app/android` directory:

```bash
# On Linux/macOS
./gradlew assembleDebug

# On Windows PowerShell
.\gradlew.bat assembleDebug
```

### 2. Install on Connected ARCore Device

Ensure USB Debugging is enabled on your Android device:

```bash
# Install Debug APK
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 🧪 Running Local Unit Tests

Run local JVM unit tests for corridor distance geometry, severity scoring, and voice intent classification:

```bash
./gradlew test
```

---

## ♿ Accessibility Design Notes

- **Auditory & Haptic First**: Interactivity is driven primarily via speech prompts, stereo audio cues, and haptic vibrations.
- **High-Contrast Touch Targets**: UI layouts (`activity_main.xml`, `activity_place_selection.xml`) use full-width, high-contrast buttons with explicit TalkBack `contentDescription` attributes.
- **Calm Voice Delivery**: Spoken alerts use neutral, informative text without panicked punctuation.
