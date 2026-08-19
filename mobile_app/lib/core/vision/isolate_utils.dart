import 'dart:isolate';
import 'package:camera/camera.dart';
import 'package:flutter/foundation.dart';
import 'package:tflite_flutter/tflite_flutter.dart';

class IsolateData {
  final CameraImage cameraImage;
  final SendPort responsePort;
  final InterpreterAddress interpreterAddress;

  IsolateData({
    required this.cameraImage,
    required this.responsePort,
    required this.interpreterAddress,
  });
}

class InterpreterAddress {
  final int address;
  InterpreterAddress(this.address);
}

class IsolateUtils {
  static const String debugName = "InferenceIsolate";
  Isolate? _isolate;
  final ReceivePort _receivePort = ReceivePort();
  SendPort? _sendPort;

  SendPort? get sendPort => _sendPort;

  Future<void> start() async {
    _isolate = await Isolate.spawn<SendPort>(
      entryPoint,
      _receivePort.sendPort,
      debugName: debugName,
    );

    _sendPort = await _receivePort.first;
  }

  static void entryPoint(SendPort sendPort) async {
    final port = ReceivePort();
    sendPort.send(port.sendPort);

    await for (final IsolateData isolateData in port) {
      try {
        final Interpreter interpreter = Interpreter.fromAddress(isolateData.interpreterAddress.address);
        
        // Convert CameraImage to RGB Tensor buffer
        // Note: Real YUV to RGB conversion would happen here.
        // For demonstration, we simulate output of YOLOv8
        // YOLOv8 output is [1, 84, 8400] roughly.
        
        // Simulating processing
        await Future.delayed(const Duration(milliseconds: 50));
        
        // Send back raw output or processed DetectedObstacles
        isolateData.responsePort.send("SUCCESS");
      } catch (e) {
        isolateData.responsePort.send("ERROR: \$e");
      }
    }
  }

  void dispose() {
    _isolate?.kill(priority: Isolate.immediate);
    _isolate = null;
    _receivePort.close();
  }
}
