import 'package:camera/camera.dart';

class DetectedObstacle {
  final String label;
  final double confidence;
  final double left;
  final double top;
  final double right;
  final double bottom;

  DetectedObstacle({
    required this.label,
    required this.confidence,
    required this.left,
    required this.top,
    required this.right,
    required this.bottom,
  });

  double get width => right - left;
  double get height => bottom - top;
  double get area => width * height;
  double get centerX => left + (width / 2);
  double get centerY => top + (height / 2);
}

abstract class ObstacleDetector {
  Future<void> initialize();
  Stream<List<DetectedObstacle>> processFrame(CameraImage frame);
  Future<void> dispose();
}
