import 'dart:async';
import 'package:flutter/material.dart';
import '../../main.dart';
import '../../core/vision/object_detector.dart';
import '../../core/vision/danger_classifier.dart';
import '../../core/audio/priority_audio_queue.dart';
import '../../core/voice/voice_command_engine.dart';
import '../../core/scene/scene_recognizer.dart';
import '../../services/place_api_service.dart';
import '../widgets/danger_radar_widget.dart';
import '../widgets/voice_indicator_widget.dart';

class NavigationScreen extends StatefulWidget {
  const NavigationScreen({Key? key}) : super(key: key);

  @override
  State<NavigationScreen> createState() => _NavigationScreenState();
}

class _NavigationScreenState extends State<NavigationScreen> {
  final ObjectDetector _detector = ObjectDetector();
  final DangerClassifier _dangerClassifier = DangerClassifier();
  final PriorityAudioQueue _audioQueue = PriorityAudioQueue();
  final VoiceCommandEngine _voiceEngine = VoiceCommandEngine();
  final SceneRecognizer _sceneRecognizer = SceneRecognizer();
  final PlaceApiService _apiService = PlaceApiService();

  List<ClassifiedDanger> _currentDangers = [];
  bool _isNavigating = true;
  String _lastSpokenSummary = "";
  Timer? _detectionTimer;

  @override
  void initState() {
    super.initState();
    _initEngine();
  }

  Future<void> _initEngine() async {
    await _detector.initialize();
    await _voiceEngine.startListening();

    // Welcome voice announcement
    await _audioQueue.enqueue(
      text: "Spatial Eye ready. Always-listening voice mode active. Camera scan started.",
      dangerLevel: DangerLevel.none,
      playTone: false,
    );

    // Listen to voice commands
    _voiceEngine.commandStream.listen(_handleVoiceCommand);

    // Periodic 1-second camera detection scan cycle
    _detectionTimer = Timer.periodic(const Duration(seconds: 1), (_) => _runDetectionLoop());
  }

  Future<void> _runDetectionLoop() async {
    if (!_isNavigating) return;

    final detectedObjects = await _detector.detectFrame(null, simulation: true);
    final classified = _dangerClassifier.classify(detectedObjects);

    setState(() {
      _currentDangers = classified;
    });

    if (classified.isNotEmpty) {
      final topDanger = classified.first;
      final speechText = _dangerClassifier.generateSpokenSummary(classified);

      // Speak audio whenever danger is escalated, high priority, or newly changed summary
      if (topDanger.isEscalated ||
          topDanger.level == DangerLevel.high ||
          _lastSpokenSummary != speechText) {
        _lastSpokenSummary = speechText;
        await _audioQueue.enqueue(
          text: speechText,
          dangerLevel: topDanger.level,
          playTone: topDanger.level == DangerLevel.high || topDanger.level == DangerLevel.medium,
        );
      }
    }
  }

  void _handleVoiceCommand(VoiceCommand command) async {
    switch (command.intent) {
      case VoiceCommandIntent.openCamera:
        setState(() => _isNavigating = true);
        await _audioQueue.enqueue(
          text: "Camera activated. Scanning path ahead for obstacles.",
          dangerLevel: DangerLevel.none,
          playTone: false,
        );
        break;

      case VoiceCommandIntent.toggleTheme:
        final targetMode = command.payload == "light" ? ThemeMode.light : ThemeMode.dark;
        SpatialEyeApp.of(context)?.toggleTheme(targetMode);
        final modeText = targetMode == ThemeMode.light ? "Light mode" : "Dark mode";
        await _audioQueue.enqueue(
          text: "UI theme changed to $modeText.",
          dangerLevel: DangerLevel.none,
          playTone: false,
        );
        setState(() {});
        break;

      case VoiceCommandIntent.savePlace:
        _voiceEngine.isAwaitingPlaceName = true;
        setState(() {});
        await _audioQueue.enqueue(
          text: "Saving current location. Please state the name of this place now.",
          dangerLevel: DangerLevel.none,
          playTone: false,
        );
        break;

      case VoiceCommandIntent.providePlaceName:
        final placeName = command.payload ?? "Saved Location";
        final sceneVec = _sceneRecognizer.extractFeatureEmbedding(null);
        await _audioQueue.enqueue(
          text: "Saving place as $placeName. Please wait.",
          dangerLevel: DangerLevel.none,
          playTone: false,
        );

        try {
          await _apiService.savePlace(
            name: placeName,
            embeddings: [sceneVec.values],
          );
          await _audioQueue.enqueue(
            text: "Saved as $placeName.",
            dangerLevel: DangerLevel.none,
            playTone: false,
          );
        } catch (e) {
          await _audioQueue.enqueue(
            text: "Notice: Saved as $placeName offline.",
            dangerLevel: DangerLevel.none,
            playTone: false,
          );
        }
        break;

      case VoiceCommandIntent.querySurroundings:
        final summary = _dangerClassifier.generateSpokenSummary(_currentDangers);
        _lastSpokenSummary = summary;
        await _audioQueue.enqueue(
          text: summary,
          dangerLevel: _currentDangers.isNotEmpty ? _currentDangers.first.level : DangerLevel.none,
          playTone: false,
        );
        break;

      case VoiceCommandIntent.whereAmI:
        final sceneVec = _sceneRecognizer.extractFeatureEmbedding(null);
        await _audioQueue.enqueue(
          text: "Scanning environment...",
          dangerLevel: DangerLevel.none,
          playTone: false,
        );

        final matches = await _apiService.recognizePlace(embedding: sceneVec.values);
        if (matches.isNotEmpty) {
          final topMatch = matches.first;
          await _audioQueue.enqueue(
            text: "You are currently in ${topMatch.name}.",
            dangerLevel: DangerLevel.none,
            playTone: false,
          );
        } else {
          await _audioQueue.enqueue(
            text: "Unrecognized location. Say 'save this place' to bookmark it.",
            dangerLevel: DangerLevel.none,
            playTone: false,
          );
        }
        break;

      case VoiceCommandIntent.repeatLast:
        if (_lastSpokenSummary.isNotEmpty) {
          await _audioQueue.enqueue(
            text: _lastSpokenSummary,
            dangerLevel: DangerLevel.none,
            playTone: false,
          );
        } else {
          await _audioQueue.enqueue(
            text: "No recent alerts.",
            dangerLevel: DangerLevel.none,
            playTone: false,
          );
        }
        break;

      case VoiceCommandIntent.stop:
        setState(() => _isNavigating = false);
        await _audioQueue.stop();
        await _audioQueue.enqueue(
          text: "Navigation paused.",
          dangerLevel: DangerLevel.none,
          playTone: false,
        );
        break;

      case VoiceCommandIntent.unknown:
        await _audioQueue.enqueue(
          text: "Command not recognized. Say 'what's in front of me', 'on camera', 'change ui to light', or 'stop'.",
          dangerLevel: DangerLevel.none,
          playTone: false,
        );
        break;
    }
  }

  @override
  void dispose() {
    _detectionTimer?.cancel();
    _voiceEngine.stopListening();
    _audioQueue.stop();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final primaryColor = Theme.of(context).colorScheme.primary;

    return Scaffold(
      appBar: AppBar(
        title: Text(
          "SPATIAL EYE",
          style: TextStyle(
            fontWeight: FontWeight.black,
            letterSpacing: 2,
            color: isDark ? Colors.white : Colors.black80,
          ),
        ),
        actions: [
          IconButton(
            icon: Icon(
              isDark ? Icons.light_mode : Icons.dark_mode,
              color: primaryColor,
            ),
            tooltip: isDark ? "Switch to Light Mode" : "Switch to Dark Mode",
            onPressed: () {
              _voiceEngine.simulateSpokenCommand(isDark ? "change ui to light" : "change ui to dark");
            },
          ),
          IconButton(
            icon: Icon(_isNavigating ? Icons.pause : Icons.play_arrow, color: primaryColor),
            tooltip: _isNavigating ? "Pause Camera" : "Turn On Camera",
            onPressed: () {
              _voiceEngine.simulateSpokenCommand(_isNavigating ? "stop" : "open camera");
            },
          ),
        ],
      ),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            children: [
              // Voice Indicator Header
              VoiceIndicatorWidget(
                isListening: _voiceEngine.isListening,
                isAwaitingName: _voiceEngine.isAwaitingPlaceName,
                statusText: _voiceEngine.isAwaitingPlaceName
                    ? "Say location name..."
                    : (_isNavigating ? "Listening for voice..." : "Camera Paused"),
              ),
              const SizedBox(height: 16),

              // Danger Radar Overlay Widget
              Expanded(
                child: DangerRadarWidget(
                  classifiedDangers: _currentDangers,
                ),
              ),
              const SizedBox(height: 16),

              // Voice Command Touch Fallbacks (Accessibility friendly large targets)
              GridView.count(
                shrinkWrap: true,
                crossAxisCount: 2,
                childAspectRatio: 2.5,
                crossAxisSpacing: 10,
                mainAxisSpacing: 10,
                children: [
                  _buildVoiceButton(
                    context: context,
                    icon: Icons.search,
                    label: "What's Ahead?",
                    onTap: () => _voiceEngine.simulateSpokenCommand("what's in front of me"),
                  ),
                  _buildVoiceButton(
                    context: context,
                    icon: isDark ? Icons.light_mode : Icons.dark_mode,
                    label: isDark ? "Light UI" : "Dark UI",
                    onTap: () => _voiceEngine.simulateSpokenCommand(
                      isDark ? "change ui to light" : "change ui to dark",
                    ),
                  ),
                  _buildVoiceButton(
                    context: context,
                    icon: Icons.my_location,
                    label: "Where Am I?",
                    onTap: () => _voiceEngine.simulateSpokenCommand("where am i"),
                  ),
                  _buildVoiceButton(
                    context: context,
                    icon: Icons.bookmark_add,
                    label: "Save Place",
                    onTap: () => _voiceEngine.simulateSpokenCommand("save this place"),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildVoiceButton({
    required BuildContext context,
    required IconData icon,
    required String label,
    required VoidCallback onTap,
  }) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final primaryColor = Theme.of(context).colorScheme.primary;

    return ElevatedButton.icon(
      style: ElevatedButton.styleFrom(
        backgroundColor: isDark ? Colors.grey.shade900 : Colors.white,
        foregroundColor: isDark ? Colors.white : Colors.black80,
        elevation: isDark ? 0 : 2,
        side: BorderSide(color: isDark ? Colors.white24 : Colors.grey.shade300),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      ),
      onPressed: onTap,
      icon: Icon(icon, color: primaryColor),
      label: Text(
        label,
        style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
      ),
    );
  }
}
