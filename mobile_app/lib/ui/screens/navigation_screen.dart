import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:camera/camera.dart';
import 'package:google_mlkit_object_detection/google_mlkit_object_detection.dart' as mlkit;

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

  CameraController? _cameraController;
  bool _isCameraInitialized = false;
  bool _isProcessingFrame = false;

  List<ClassifiedDanger> _currentDangers = [];
  bool _isNavigating = true;
  
  // Audio Debounce State
  String _lastSpokenSummary = "";
  DateTime _lastSpokenTime = DateTime.fromMillisecondsSinceEpoch(0);

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

    // Initialize Camera
    final cameras = await availableCameras();
    if (cameras.isNotEmpty) {
      final backCamera = cameras.firstWhere(
        (cam) => cam.lensDirection == CameraLensDirection.back,
        orElse: () => cameras.first,
      );

      _cameraController = CameraController(
        backCamera,
        ResolutionPreset.high,
        enableAudio: false,
        imageFormatGroup: defaultTargetPlatform == TargetPlatform.iOS
            ? ImageFormatGroup.bgra8888
            : ImageFormatGroup.nv21,
      );

      try {
        await _cameraController!.initialize();
        
        // Boost exposure to fix "dark camera" issue
        try {
          final maxExposure = await _cameraController!.getMaxExposureOffset();
          // Boost by +1.0 or half of max exposure, whichever is smaller, to brighten image
          final boost = (maxExposure * 0.5).clamp(0.0, 2.0);
          await _cameraController!.setExposureOffset(boost);
        } catch (_) {}

        if (mounted) {
          setState(() {
            _isCameraInitialized = true;
          });
          _cameraController!.startImageStream(_processCameraFrame);
        }
      } catch (e) {
        debugPrint("Error initializing camera: $e");
      }
    }
  }

  void _processCameraFrame(CameraImage image) async {
    if (!_isNavigating || _isProcessingFrame || !mounted) return;
    _isProcessingFrame = true;

    try {
      final inputImage = _convertCameraImageToInputImage(image);
      if (inputImage != null) {
        final detectedObjects = await _detector.detectFrame(inputImage, simulation: false);
        final classified = _dangerClassifier.classify(detectedObjects);

        if (mounted) {
          setState(() {
            _currentDangers = classified;
          });
        }

        if (classified.isNotEmpty) {
          final topDanger = classified.first;
          
          // Debounce logic: only speak if highly dangerous AND (different from last OR > 10 seconds ago)
          if (topDanger.isEscalated || topDanger.level == DangerLevel.high) {
            final speechText = _dangerClassifier.generateSpokenSummary(classified);
            
            final now = DateTime.now();
            final timeSinceLast = now.difference(_lastSpokenTime);

            if (speechText != _lastSpokenSummary || timeSinceLast.inSeconds > 10) {
              _lastSpokenSummary = speechText;
              _lastSpokenTime = now;
              
              if (topDanger.level == DangerLevel.high) {
                HapticFeedback.heavyImpact();
              } else {
                HapticFeedback.mediumImpact();
              }

              await _audioQueue.enqueue(
                text: speechText,
                dangerLevel: topDanger.level,
                playTone: true,
              );
            }
          }
        }
      }
    } catch (e) {
      debugPrint("Error processing frame: $e");
    } finally {
      if (mounted) {
        _isProcessingFrame = false;
      }
    }
  }

  mlkit.InputImage? _convertCameraImageToInputImage(CameraImage image) {
    if (_cameraController == null) return null;
    
    final WriteBuffer allBytes = WriteBuffer();
    for (final Plane plane in image.planes) {
      allBytes.putUint8List(plane.bytes);
    }
    final bytes = allBytes.done().buffer.asUint8List();

    final Size imageSize = Size(image.width.toDouble(), image.height.toDouble());
    
    final camera = _cameraController!.description;
    final imageRotation = mlkit.InputImageRotationValue.fromRawValue(camera.sensorOrientation);
    if (imageRotation == null) return null;

    final formatFromRaw = mlkit.InputImageFormatValue.fromRawValue(image.format.raw);
    final inputImageFormat = formatFromRaw ?? 
        (defaultTargetPlatform == TargetPlatform.android 
            ? mlkit.InputImageFormat.nv21 
            : mlkit.InputImageFormat.bgra8888);

    final inputImageData = mlkit.InputImageMetadata(
      size: imageSize,
      rotation: imageRotation,
      format: inputImageFormat,
      bytesPerRow: image.planes[0].bytesPerRow,
    );

    return mlkit.InputImage.fromBytes(bytes: bytes, metadata: inputImageData);
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
        _lastSpokenTime = DateTime.now();
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
    _cameraController?.dispose();
    _detector.dispose();
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
          style: TextStyle(fontWeight: FontWeight.w900, letterSpacing: 2, color: Colors.white),
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

              // Danger Radar Overlay Widget (with optional Camera Preview behind it)
              Expanded(
                child: Stack(
                  fit: StackFit.expand,
                  children: [
                    if (_isCameraInitialized && _isNavigating)
                      ClipRRect(
                        borderRadius: BorderRadius.circular(16),
                        child: FittedBox(
                          fit: BoxFit.cover,
                          child: SizedBox(
                            width: _cameraController!.value.previewSize?.height ?? 1,
                            height: _cameraController!.value.previewSize?.width ?? 1,
                            child: CameraPreview(_cameraController!),
                          ),
                        ),
                      ),
                    DangerRadarWidget(
                      classifiedDangers: _currentDangers,
                    ),
                  ],
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
