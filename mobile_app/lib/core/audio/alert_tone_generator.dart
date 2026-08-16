import '../vision/danger_classifier.dart';

abstract class ToneAudioPlayer {
  Future<void> playLowDangerBeep();
  Future<void> playMediumDangerBeep();
  Future<void> playHighDangerBeep();
}

class SimulatedTonePlayer implements ToneAudioPlayer {
  @override
  Future<void> playLowDangerBeep() async {
    // Single soft low pitch tone (440Hz chime pattern)
    print("🔊 [AUDIO TONE] LOW DANGER BEEP (Single Soft Chime: 440 Hz)");
  }

  @override
  Future<void> playMediumDangerBeep() async {
    // Double mid pitch beep (880Hz double pulse)
    print("🔊🔊 [AUDIO TONE] MEDIUM DANGER BEEP (Double Pulse: 880 Hz)");
  }

  @override
  Future<void> playHighDangerBeep() async {
    // Rapid high pitch repeating siren/beep (1760Hz rapid triple alert)
    print("🚨🚨🚨 [AUDIO TONE] HIGH DANGER ALERT SIREN (Rapid High Pulse: 1760 Hz)");
  }
}

class AlertToneGenerator {
  final ToneAudioPlayer _player;

  AlertToneGenerator({ToneAudioPlayer? player})
      : _player = player ?? SimulatedTonePlayer();

  Future<void> playDangerTone(DangerLevel level) async {
    switch (level) {
      case DangerLevel.high:
        await _player.playHighDangerBeep();
        break;
      case DangerLevel.medium:
        await _player.playMediumDangerBeep();
        break;
      case DangerLevel.low:
        await _player.playLowDangerBeep();
        break;
      case DangerLevel.none:
        break;
    }
  }
}
