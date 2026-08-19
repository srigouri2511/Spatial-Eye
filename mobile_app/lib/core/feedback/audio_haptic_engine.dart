import 'package:audioplayers/audioplayers.dart';
import 'package:flutter_tts/flutter_tts.dart';
import 'package:vibration/vibration.dart';
import 'threat_prioritizer.dart';

class AudioHapticEngine {
  final AudioPlayer _audioPlayer = AudioPlayer();
  final FlutterTts _tts = FlutterTts();
  
  Map<String, DateTime> _debounceMap = {};
  bool _quietMode = false;

  Future<void> initialize() async {
    await _tts.setLanguage("en-US");
    await _tts.setSpeechRate(0.55);
    await _tts.setVolume(1.0);
    await _tts.setPitch(1.0);
    // Make sure audio player balance is supported if possible, audioplayers supports it on some platforms
  }

  void toggleQuietMode() {
    _quietMode = !_quietMode;
    if (_quietMode) {
      Vibration.vibrate(duration: 100, amplitude: 128);
    } else {
      speak("Quiet mode disabled");
    }
  }

  bool get quietMode => _quietMode;

  Future<void> processThreat(AnalyzedThreat threat) async {
    final now = DateTime.now();
    final obstacleKey = "${threat.obstacle.label}_${threat.zone.name}";

    // Context-Aware Debouncing: Critical bypasses debounce. Normal: 6 seconds.
    if (threat.level != ThreatLevel.critical) {
      if (_debounceMap.containsKey(obstacleKey)) {
        if (now.difference(_debounceMap[obstacleKey]!).inSeconds < 6) {
          return;
        }
      }
    }
    _debounceMap[obstacleKey] = now;

    // Haptic Feedback
    bool hasVibrator = await Vibration.hasVibrator() ?? false;
    if (hasVibrator) {
      if (threat.level == ThreatLevel.critical) {
        Vibration.vibrate(duration: 1000, amplitude: 255); // Continuous heavy
      } else if (threat.level == ThreatLevel.medium) {
        Vibration.vibrate(duration: 200, amplitude: 128); // Moderate
      } else {
        Vibration.vibrate(duration: 50, amplitude: 64); // Light ticking
      }
    }

    if (_quietMode) return;

    // Spatial Audio Panning (using balance)
    double balance = 0.0;
    if (threat.zone == ThreatZone.left) balance = -0.8;
    if (threat.zone == ThreatZone.right) balance = 0.8;
    
    await _audioPlayer.setBalance(balance);
    
    // Simulate playing a ping whose rate varies with distance
    // double playbackRate = 1.0 + (1.0 - threat.distanceEstimate);
    // await _audioPlayer.setPlaybackRate(playbackRate);
    // await _audioPlayer.play(AssetSource('audio/ping.mp3'));

    // Vocal announcement
    String position = threat.zone.name;
    await _tts.speak("${threat.obstacle.label} $position");
  }

  Future<void> speak(String message) async {
    if (_quietMode) return;
    await _tts.speak(message);
  }

  void dispose() {
    _audioPlayer.dispose();
    _tts.stop();
  }
}
