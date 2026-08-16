import 'dart:async';

enum VoiceCommandIntent {
  openCamera,
  savePlace,
  querySurroundings,
  whereAmI,
  repeatLast,
  stop,
  providePlaceName,
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

    if (text.contains("open camera") || text.contains("start camera") || text.contains("turn on camera")) {
      return VoiceCommand(intent: VoiceCommandIntent.openCamera, rawText: transcript);
    }
    if (text.contains("save this place") || text.contains("save location") || text.contains("save room") || text.contains("bookmark place")) {
      return VoiceCommand(intent: VoiceCommandIntent.savePlace, rawText: transcript);
    }
    if (text.contains("what's in front") || text.contains("what is in front") || text.contains("scan ahead") || text.contains("describe")) {
      return VoiceCommand(intent: VoiceCommandIntent.querySurroundings, rawText: transcript);
    }
    if (text.contains("where am i") || text.contains("recognize place") || text.contains("which room")) {
      return VoiceCommand(intent: VoiceCommandIntent.whereAmI, rawText: transcript);
    }
    if (text.contains("repeat") || text.contains("say again")) {
      return VoiceCommand(intent: VoiceCommandIntent.repeatLast, rawText: transcript);
    }
    if (text.contains("stop") || text.contains("pause") || text.contains("be quiet") || text.contains("silence")) {
      return VoiceCommand(intent: VoiceCommandIntent.stop, rawText: transcript);
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
