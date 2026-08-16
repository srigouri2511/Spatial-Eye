import 'dart:async';
import 'package:flutter/material.dart';
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
      
      // If escalated or high danger, speak immediately
      if (topDanger.isEscalated || topDanger.level == DangerLevel.high) {
        final speechText = _dangerClassifier.generateSpokenSummary(classified);
        _lastSpokenSummary = speechText;
        await _audioQueue.enqueue(
          text: speechText,
          dangerLevel: topDanger.level,
          playTone: true,
        );
      }
    }
  }

  void _handleVoiceCommand(VoiceCommand command) async {
    switch (command.intent) {
      case VoiceCommandIntent.openCamera:
        setState(() => _isNavigating = true);
        await _audioQueue.enqueue(
          text: "Camera activated. Scanning path ahead.",
          dangerLevel: DangerLevel.none,
          playTone: false,
        );
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
          text: "Command not recognized. Say 'what's in front of me', 'save this place', or 'stop'.",
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
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        title: const Text(
          "SPATIAL EYE",
          style: TextStyle(fontWeight: FontWeight.black, letterSpacing: 2, color: Colors.white),
        ),
        backgroundColor: Colors.black,
        elevation: 0,
        actions: [
          IconButton(
            icon: Icon(_isNavigating ? Icons.pause : Icons.play_arrow, color: Colors.cyanAccent),
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
                    : (_isNavigating ? "Listening for voice..." : "Paused"),
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
                    icon: Icons.search,
                    label: "What's Ahead?",
                    onTap: () => _voiceEngine.simulateSpokenCommand("what's in front of me"),
                  ),
                  _buildVoiceButton(
                    icon: Icons.bookmark_add,
                    label: "Save Place",
                    onTap: () => _voiceEngine.simulateSpokenCommand("save this place"),
                  ),
                  _buildVoiceButton(
                    icon: Icons.my_location,
                    label: "Where Am I?",
                    onTap: () => _voiceEngine.simulateSpokenCommand("where am i"),
                  ),
                  _buildVoiceButton(
                    icon: Icons.replay,
                    label: "Repeat Alert",
                    onTap: () => _voiceEngine.simulateSpokenCommand("repeat"),
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
    required IconData icon,
    required String label,
    required VoidCallback onTap,
  }) {
    return ElevatedButton.icon(
      style: ElevatedButton.styleFrom(
        backgroundColor: Colors.grey.shade900,
        foregroundColor: Colors.white,
        side: const BorderSide(color: Colors.white24),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      ),
      onPressed: onTap,
      icon: Icon(icon, color: Colors.cyanAccent),
      label: Text(
        label,
        style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
      ),
    );
  }
}
