import 'dart:async';
import 'package:flutter_tts/flutter_tts.dart';
import '../vision/danger_classifier.dart';
import 'alert_tone_generator.dart';

enum AudioPriority {
  low,
  medium,
  high,
}

class AudioMessage {
  final String text;
  final DangerLevel dangerLevel;
  final AudioPriority priority;
  final bool isToneOnly;
  final DateTime timestamp;

  AudioMessage({
    required this.text,
    required this.dangerLevel,
    required this.priority,
    this.isToneOnly = false,
  }) : timestamp = DateTime.now();
}

class PriorityAudioQueue {
  final FlutterTts _flutterTts = FlutterTts();
  final AlertToneGenerator _toneGenerator = AlertToneGenerator();
  
  bool _isSpeaking = false;
  AudioPriority _currentSpeakingPriority = AudioPriority.low;
  
  double speechRate = 0.5;
  double speechVolume = 1.0;
  double speechPitch = 1.0;

  final List<AudioMessage> _queue = [];

  PriorityAudioQueue() {
    _initTts();
  }

  Future<void> _initTts() async {
    try {
      await _flutterTts.setSpeechRate(speechRate);
      await _flutterTts.setVolume(speechVolume);
      await _flutterTts.setPitch(speechPitch);
      await _flutterTts.setLanguage("en-US");

      _flutterTts.setCompletionHandler(() {
        _isSpeaking = false;
        _processQueue();
      });

      _flutterTts.setErrorHandler((msg) {
        _isSpeaking = false;
        _processQueue();
      });
    } catch (e) {
      print("TTS Init Notice (Running in fallback mode): $e");
    }
  }

  /// Speaks or plays an alert message with priority queuing and preemption
  Future<void> enqueue({
    required String text,
    required DangerLevel dangerLevel,
    bool playTone = true,
  }) async {
    AudioPriority priority = AudioPriority.low;
    if (dangerLevel == DangerLevel.high) {
      priority = AudioPriority.high;
    } else if (dangerLevel == DangerLevel.medium) {
      priority = AudioPriority.medium;
    }

    final message = AudioMessage(
      text: text,
      dangerLevel: dangerLevel,
      priority: priority,
    );

    // If a High Danger alert arrives while speaking lower priority, interrupt immediately!
    if (priority == AudioPriority.high &&
        _isSpeaking &&
        _currentSpeakingPriority != AudioPriority.high) {
      print("⚡ [AUDIO QUEUE] Interrupting lower priority audio for HIGH DANGER alert!");
      await stop();
      _queue.insert(0, message);
      if (playTone) {
        await _toneGenerator.playDangerTone(dangerLevel);
      }
      await _speak(message);
      return;
    }

    // Otherwise play tone and queue speech
    if (playTone && dangerLevel != DangerLevel.none) {
      await _toneGenerator.playDangerTone(dangerLevel);
    }

    _queue.add(message);
    _queue.sort((a, b) => b.priority.index.compareTo(a.priority.index));

    if (!_isSpeaking) {
      _processQueue();
    }
  }

  Future<void> _processQueue() async {
    if (_queue.isEmpty || _isSpeaking) return;

    final nextMessage = _queue.removeAt(0);
    await _speak(nextMessage);
  }

  Future<void> _speak(AudioMessage message) async {
    _isSpeaking = true;
    _currentSpeakingPriority = message.priority;
    print("🗣️ [TTS SPEAKING] (Priority: ${message.priority.name}): ${message.text}");

    try {
      await _flutterTts.speak(message.text);
    } catch (e) {
      // Print fallback log if native engine not present
      print("🗣️ [TTS FALLBACK]: ${message.text}");
      await Future.delayed(const Duration(milliseconds: 1500));
      _isSpeaking = false;
      _processQueue();
    }
  }

  Future<void> stop() async {
    _isSpeaking = false;
    _queue.clear();
    try {
      await _flutterTts.stop();
    } catch (_) {}
  }
}
