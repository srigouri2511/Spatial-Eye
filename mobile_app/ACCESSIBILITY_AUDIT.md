# Spatial Eye - Accessibility Audit

## Semantic Mapping
- **TalkBack/VoiceOver Integration**: Ensure all visual elements, especially the Diagnostic HUD, have appropriate semantic labels.
- **Invisible Gesture Pad**: The main UI has no visual buttons. Semantic labels must explain the available gestures when the screen is focused.

## Gesture Matrix
| Gesture | Action |
|---|---|
| Single Tap | Repeat last audio status / current clear path |
| Double Tap | Pause/Resume active navigation radar |
| Two-Finger Tap | Announce battery, thermals, and camera health |
| Long Press | Toggle quiet mode (haptics only, mute sound pings) |

## Spatial Audio Panning Frequencies
- **Left**: Balance -0.8
- **Center**: Balance 0.0
- **Right**: Balance +0.8
- **Proximity Mapping**: Closer objects emit faster, higher-frequency pulses.

## Haptic Intensity Profiles
- **> 3 meters**: Light, infrequent ticking.
- **1.5 - 3 meters**: Moderate, steady vibration.
- **< 1.5 meters (Critical)**: Continuous heavy vibration.
