import 'dart:async';

enum VoiceCommandIntent {
  openCamera,
  savePlace,
  querySurroundings,
  whereAmI,
  repeatLast,
  stop,
  providePlaceName,
  toggleTheme,
  unknown
}

class VoiceCommand {
  final VoiceCommandIntent intent;
  final String rawText;
  final String? payload;

  VoiceCommand({
    required this.intent,
    required this.rawText,
    this.payload,
  });
}

class VoiceCommandEngine {
  bool _isListening = false;
  bool get isListening => _isListening;

  bool isAwaitingPlaceName = false;

  final _commandStreamController = StreamController<VoiceCommand>.broadcast();
  Stream<VoiceCommand> get commandStream => _commandStreamController.stream;

  Future<void> startListening() async {
    _isListening = true;
    print("🎙️ [VOICE ENGINE] Always-listening engine STARTED...");
  }

  Future<void> stopListening() async {
    _isListening = false;
    print("🎙️ [VOICE ENGINE] Always-listening engine STOPPED.");
  }

  /// Parses voice STT transcript into structured VoiceCommand intent
  VoiceCommand parseTranscript(String transcript) {
    final text = transcript.toLowerCase().trim();

    if (isAwaitingPlaceName) {
      isAwaitingPlaceName = false;
      return VoiceCommand(
        intent: VoiceCommandIntent.providePlaceName,
        rawText: transcript,
        payload: transcript,
      );
    }

    // Stop / camera off keywords (check before camera on to avoid "turn off camera" matching "camera")
    if (text.contains("stop") ||
        text.contains("pause") ||
        text.contains("be quiet") ||
        text.contains("silence") ||
        text.contains("turn off camera") ||
        text.contains("off camera") ||
        text.contains("camera off") ||
        text.contains("close camera")) {
      return VoiceCommand(intent: VoiceCommandIntent.stop, rawText: transcript);
    }

    // Open camera / camera on keywords ("on camera", "whn i on the camera", "on the camera", etc.)
    if (text.contains("open camera") ||
        text.contains("start camera") ||
        text.contains("turn on camera") ||
        text.contains("turn camera on") ||
        text.contains("on camera") ||
        text.contains("on the camera") ||
        text.contains("camera on") ||
        text.contains("activate camera") ||
        text.contains("enable camera") ||
        text.contains("camera")) {
      return VoiceCommand(intent: VoiceCommandIntent.openCamera, rawText: transcript);
    }

    // Theme toggle commands: Light UI Mode
    if (text.contains("change ui to light") ||
        text.contains("light mode") ||
        text.contains("light theme") ||
        text.contains("switch to light") ||
        text.contains("turn on light") ||
        text.contains("light ui")) {
      return VoiceCommand(
        intent: VoiceCommandIntent.toggleTheme,
        rawText: transcript,
        payload: "light",
      );
    }

    // Theme toggle commands: Dark UI Mode
    if (text.contains("change ui to dark") ||
        text.contains("dark mode") ||
        text.contains("dark theme") ||
        text.contains("switch to dark") ||
        text.contains("turn on dark") ||
        text.contains("dark ui")) {
      return VoiceCommand(
        intent: VoiceCommandIntent.toggleTheme,
        rawText: transcript,
        payload: "dark",
      );
    }

    if (text.contains("save this place") ||
        text.contains("save location") ||
        text.contains("save room") ||
        text.contains("bookmark place")) {
      return VoiceCommand(intent: VoiceCommandIntent.savePlace, rawText: transcript);
    }

    if (text.contains("what's in front") ||
        text.contains("what is in front") ||
        text.contains("scan ahead") ||
        text.contains("describe") ||
        text.contains("what do you see") ||
        text.contains("detect objects") ||
        text.contains("check obstacles") ||
        text.contains("scan")) {
      return VoiceCommand(intent: VoiceCommandIntent.querySurroundings, rawText: transcript);
    }

    if (text.contains("where am i") ||
        text.contains("recognize place") ||
        text.contains("which room") ||
        text.contains("what room is this")) {
      return VoiceCommand(intent: VoiceCommandIntent.whereAmI, rawText: transcript);
    }

    if (text.contains("repeat") || text.contains("say again") || text.contains("repeat alert")) {
      return VoiceCommand(intent: VoiceCommandIntent.repeatLast, rawText: transcript);
    }

    return VoiceCommand(intent: VoiceCommandIntent.unknown, rawText: transcript);
  }

  /// Simulates spoken voice command input (for dev/voice testing)
  void simulateSpokenCommand(String spokenText) {
    final cmd = parseTranscript(spokenText);
    print("🎤 [VOICE COMMAND RECOGNIZED]: '${cmd.rawText}' -> Intent: ${cmd.intent.name}");
    _commandStreamController.add(cmd);
  }
}
