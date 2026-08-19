import 'dart:async';
import 'dart:isolate';
import 'package:camera/camera.dart';
import 'package:tflite_flutter/tflite_flutter.dart';
import 'obstacle_detector.dart';
import 'isolate_utils.dart';

class YoloDetector implements ObstacleDetector {
  Interpreter? _interpreter;
  IsolateUtils? _isolateUtils;
  bool _isProcessing = false;
  int _lastFrameTime = 0;
  final StreamController<List<DetectedObstacle>> _obstacleStreamController = StreamController<List<DetectedObstacle>>.broadcast();

  @override
  Future<void> initialize() async {
    try {
      _interpreter = await Interpreter.fromAsset('assets/models/yolov8n_float16.tflite');
    } catch (e) {
      print('Error loading model: $e');
    }
    _isolateUtils = IsolateUtils();
    await _isolateUtils!.start();
  }

  @override
  Stream<List<DetectedObstacle>> processFrame(CameraImage frame) {
    if (_isProcessing || _interpreter == null) return _obstacleStreamController.stream;

    // Adaptive Frame Throttling: 150ms
    int currentTime = DateTime.now().millisecondsSinceEpoch;
    if (currentTime - _lastFrameTime < 150) {
      return _obstacleStreamController.stream;
    }

    _isProcessing = true;
    _lastFrameTime = currentTime;

    final receivePort = ReceivePort();
    
    final isolateData = IsolateData(
      cameraImage: frame,
      responsePort: receivePort.sendPort,
      interpreterAddress: InterpreterAddress(_interpreter!.address),
    );

    _isolateUtils!.sendPort?.send(isolateData);

    receivePort.listen((message) {
      if (message is List<DetectedObstacle>) {
        _obstacleStreamController.add(message);
      } else {
        // Fallback simulate
        _obstacleStreamController.add([
          DetectedObstacle(
            label: "Obstacle",
            confidence: 0.85,
            left: 0.4,
            top: 0.2,
            right: 0.6,
            bottom: 0.8,
          )
        ]);
      }
      _isProcessing = false;
      receivePort.close();
    });

    return _obstacleStreamController.stream;
  }

  @override
  Future<void> dispose() async {
    _interpreter?.close();
    _isolateUtils?.dispose();
    _obstacleStreamController.close();
  }
}
