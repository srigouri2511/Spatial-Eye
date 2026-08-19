# Spatial Eye - Implementation Plan

## Phase 1: Native Layer & Dependency Stabilization
- [ ] Clean legacy ML Kit dependencies.
- [ ] Pin Kotlin versions and Android SDK to min 24, target 34+.
- [ ] Configure NDK / C++ paths.
- [ ] Add dependencies (`tflite_flutter`, `audioplayers`, `flutter_tts`, `vibration`, `sensors_plus`, `flutter_riverpod`).

## Phase 2: Core Vision & Modular Detection Engine
- [ ] Implement `ObstacleDetector` interface.
- [ ] Spawn dedicated Dart Background Isolate for image conversion and inference.
- [ ] Integrate YOLOv8n (FP16/INT8) via TFLite.
- [ ] Implement Adaptive Frame Throttling.

## Phase 3: Spatial Sound, Proximity, & Threat Prioritization
- [ ] Dynamic Threat Sorter (Left/Center/Right zones).
- [ ] Proximity estimation based on bounding box size.
- [ ] Filter background noise; prioritize imminent collision path objects.
- [ ] Integrate spatial audio panning (`audioplayers` stereo balance).
- [ ] Context-aware debouncing (bypass debounce for critical hazards).
- [ ] Haptic intensity mapping based on proximity.

## Phase 4: World-Class Non-Visual & Low-Vision UI/UX
- [ ] Blind Interaction Layer (Full screen gesture detector).
  - Single tap: repeat status.
  - Double tap: pause/resume.
  - Two-finger tap: diagnostics.
  - Long press: quiet mode.
- [ ] TalkBack/VoiceOver Semantic integration.
- [ ] Diagnostic HUD Layer for testers (transparent preview, Danger Radar, FPS/latency stats).
- [ ] Audio Self-Healing & Diagnostics (vocal alerts for obscured camera, etc.).

## Phase 5: Verification & QA
- [ ] `flutter analyze` 0 errors/warnings.
- [ ] Unit tests for `ThreatPrioritizer`, `SpatialAudioMapper`, and `DangerClassifier`.
- [ ] `README.md` setup and test guide.
