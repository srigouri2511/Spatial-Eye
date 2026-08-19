# Spatial Eye

An enterprise-grade, offline-first Flutter assistive navigation app for the visually impaired. It translates the visual world into spatial audio and haptic feedback.

## Features
- **Zero-Latency Core Vision**: Uses quantized YOLOv8n (FP16) via TFLite running in a background isolate.
- **Adaptive Frame Throttling**: Preserves battery by modulating inference based on thermal constraints and scene shift.
- **Threat Prioritization Engine**: Slices the camera frame into spatial zones, estimates proximity, and filters noise for critical collision threats.
- **Blind Interaction Layer**: The entire screen serves as an invisible gesture pad (Single tap, Double tap, Two-finger tap, Long press).
- **Audio/Haptic Engine**: Proximity maps dynamically to vibration intensity and spatial audio panning.

## Setup Guide

### 1. Prerequisites
- Flutter SDK (3.x+)
- Android SDK (API 34) and NDK installed via Android Studio.
- Python 3.10+ (for model export)

### 2. Export the TFLite Model
The app runs completely offline and requires the YOLOv8n weights. We have provided an export script to convert the official PyTorch model to TFLite float16.
1. Run `pip install ultralytics`
2. Run `python export_yolo.py`
3. A `yolov8n_float16.tflite` file will be generated. Move it into `assets/models/`.

### 3. Run the App
```bash
flutter pub get
flutter run
```

### Testing on Android Emulator
To test the camera on an Android emulator:
1. Open Android Virtual Device (AVD) Manager.
2. Edit your device > Advanced Settings.
3. Under Camera, set "Back" to "VirtualScene" or "Webcam0".
4. You can simulate movement in the virtual scene using Alt + WASD.
