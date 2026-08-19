import 'dart:async';
import 'package:flutter/material.dart';
import 'package:camera/camera.dart';
import 'package:flutter/semantics.dart';

import '../../core/vision/yolo_detector.dart';
import '../../core/vision/obstacle_detector.dart';
import '../../core/feedback/threat_prioritizer.dart';
import '../../core/feedback/audio_haptic_engine.dart';

class NavigationScreen extends StatefulWidget {
  const NavigationScreen({Key? key}) : super(key: key);

  @override
  State<NavigationScreen> createState() => _NavigationScreenState();
}

class _NavigationScreenState extends State<NavigationScreen> {
  final YoloDetector _detector = YoloDetector();
  final AudioHapticEngine _engine = AudioHapticEngine();
  
  CameraController? _cameraController;
  bool _isCameraInitialized = false;
  bool _isNavigating = true;

  // HUD Data
  List<DetectedObstacle> _currentObstacles = [];
  AnalyzedThreat? _currentThreat;
  int _frameCount = 0;
  DateTime _lastFpsTime = DateTime.now();
  double _currentFps = 0.0;
  String _lastStatus = "Clear path";

  @override
  void initState() {
    super.initState();
    _initSystem();
  }

  Future<void> _initSystem() async {
    await _engine.initialize();
    await _detector.initialize();

    final cameras = await availableCameras();
    if (cameras.isNotEmpty) {
      final backCamera = cameras.firstWhere(
        (cam) => cam.lensDirection == CameraLensDirection.back,
        orElse: () => cameras.first,
      );

      _cameraController = CameraController(
        backCamera,
        ResolutionPreset.low, // Lower resolution for faster inference
        enableAudio: false,
        imageFormatGroup: ImageFormatGroup.yuv420,
      );

      try {
        await _cameraController!.initialize();
        if (mounted) {
          setState(() {
            _isCameraInitialized = true;
          });
          _cameraController!.startImageStream(_processCameraFrame);
        }
      } catch (e) {
        _engine.speak("Camera obscured or failed to initialize.");
      }
    }
  }

  void _processCameraFrame(CameraImage image) {
    if (!_isNavigating || !mounted) return;

    // Calculate FPS
    _frameCount++;
    final now = DateTime.now();
    if (now.difference(_lastFpsTime).inSeconds >= 1) {
      if (mounted) {
        setState(() {
          _currentFps = _frameCount / now.difference(_lastFpsTime).inSeconds;
        });
      }
      _frameCount = 0;
      _lastFpsTime = now;
    }

    _detector.processFrame(image).listen((obstacles) async {
      if (!mounted) return;
      setState(() {
        _currentObstacles = obstacles;
        _currentThreat = ThreatPrioritizer.getMostImminentThreat(obstacles);
      });

      if (_currentThreat != null) {
        _lastStatus = "\${_currentThreat!.obstacle.label} \${_currentThreat!.zone.name}";
        await _engine.processThreat(_currentThreat!);
      } else {
        _lastStatus = "Clear path";
      }
    });
  }

  void _handleSingleTap() {
    _engine.speak(_lastStatus);
  }

  void _handleDoubleTap() {
    setState(() {
      _isNavigating = !_isNavigating;
    });
    if (_isNavigating) {
      _engine.speak("Radar resumed.");
    } else {
      _engine.speak("Radar paused.");
    }
  }

  void _handleTwoFingerTap() {
    _engine.speak("System running. Camera active. F P S is ${_currentFps.toInt()}.");
  }

  void _handleLongPress() {
    _engine.toggleQuietMode();
  }

  @override
  void dispose() {
    _cameraController?.dispose();
    _detector.dispose();
    _engine.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    // Generate background color based on threat
    Color bgColor = Colors.black;
    if (_currentThreat != null) {
      if (_currentThreat!.level == ThreatLevel.critical) {
        bgColor = Colors.red.shade900;
      } else if (_currentThreat!.level == ThreatLevel.medium) {
        bgColor = Colors.amber.shade900;
      } else {
        bgColor = Colors.green.shade900;
      }
    }

    return Scaffold(
      backgroundColor: Colors.black,
      body: Semantics(
        label: "Spatial Eye Navigation Radar. Single tap to hear status, double tap to pause, two finger tap for diagnostics, long press for quiet mode.",
        child: GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTap: _handleSingleTap,
          onDoubleTap: _handleDoubleTap,
          onLongPress: _handleLongPress,
          // Two-finger tap hack using ScaleStart
          onScaleStart: (details) {
            if (details.pointerCount == 2) {
              _handleTwoFingerTap();
            }
          },
          child: Stack(
            fit: StackFit.expand,
            children: [
              // Diagnostic HUD Layer
              if (_isCameraInitialized)
                Opacity(
                  opacity: 0.3,
                  child: CameraPreview(_cameraController!),
                ),
              
              // Radar color overlay
              AnimatedContainer(
                duration: const Duration(milliseconds: 300),
                color: bgColor.withOpacity(0.5),
              ),

              // HUD Text
              SafeArea(
                child: Padding(
                  padding: const EdgeInsets.all(16.0),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        "SPATIAL EYE HUD",
                        style: TextStyle(color: Colors.cyanAccent, fontWeight: FontWeight.bold, fontSize: 18),
                      ),
                      const SizedBox(height: 8),
                      Text(
                        "Status: \${_isNavigating ? 'ACTIVE' : 'PAUSED'}",
                        style: const TextStyle(color: Colors.white70),
                      ),
                      Text(
                        "Quiet Mode: \${_engine.quietMode ? 'ON' : 'OFF'}",
                        style: const TextStyle(color: Colors.white70),
                      ),
                      Text(
                        "FPS: \${_currentFps.toStringAsFixed(1)}",
                        style: const TextStyle(color: Colors.white70),
                      ),
                      const Spacer(),
                      if (_currentThreat != null)
                        Container(
                          padding: const EdgeInsets.all(12),
                          decoration: BoxDecoration(
                            color: Colors.black54,
                            borderRadius: BorderRadius.circular(8),
                            border: Border.all(color: Colors.white24),
                          ),
                          child: Text(
                            "Threat: \${_currentThreat!.obstacle.label} | Zone: \${_currentThreat!.zone.name} | Lvl: \${_currentThreat!.level.name}",
                            style: const TextStyle(color: Colors.white, fontSize: 16),
                          ),
                        )
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
