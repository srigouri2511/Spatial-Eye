# Spatial Eye - Project Goal

## Mission Brief
Build and ship "Spatial Eye" — an enterprise-grade, offline-first Flutter assistive navigation app for the visually impaired. It acts as an active sensory substitution system, interpreting the visual world into auditory and haptic cues.

## Core Accessibility Heuristics
- **Sensory Overload Reduction**: Debouncing non-critical alerts. The environment contains noise; the app filters for immediate, actionable threats.
- **Zero-Latency Reflex Detection**: Frame processing and haptics must occur with minimal latency (target < 150ms) to allow the user to react in real-time to immediate collision threats.

## Target Hardware Specs
- **Platform**: Android/iOS smartphones.
- **Camera**: Standard wide-angle back-facing camera.
- **Processor**: Modern smartphone SoC capable of running quantized INT8/FP16 models locally.
- **Battery**: Must run efficiently with minimal thermal throttling; inference is throttled to 100-150ms per frame.

## User Persona Constraints
- **Primary User**: Visually impaired or blind individual navigating unfamiliar environments (indoor/outdoor).
- **Interaction Model**: No reliance on visual UI. Screen acts as an invisible, full-surface gesture pad.
- **Feedback**: Spatially panned audio and dynamic haptic vibration mapping to object proximity and threat level.
