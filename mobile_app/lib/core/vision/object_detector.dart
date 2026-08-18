import 'dart:math';

enum ObstacleType {
  stairs,
  curb,
  pole,
  furniture,
  vehicle,
  door,
  person,
  pothole,
  wall,
  unknown
}

class BoundingBox {
  final double left;
  final double top;
  final double right;
  final double bottom;

  BoundingBox({
    required this.left,
    required this.top,
    required this.right,
    required this.bottom,
  });

  double get width => right - left;
  double get height => bottom - top;
  double get centerX => (left + right) / 2.0;
  double get centerY => (top + bottom) / 2.0;
}

class DetectedObject {
  final String label;
  final ObstacleType type;
  final double confidence;
  final BoundingBox bbox;
  final double distanceMeters; // Estimated distance in meters
  final String direction; // "left", "slightly left", "ahead", "slightly right", "right"

  DetectedObject({
    required this.label,
    required this.type,
    required this.confidence,
    required this.bbox,
    required this.distanceMeters,
    required this.direction,
  });

  @override
  String toString() {
    return '$label, ${distanceMeters.toStringAsFixed(1)}m, $direction';
  }
}

class ObjectDetector {
  bool _isInitialized = false;

  Future<void> initialize() async {
    // In production, loads TFLite / ONNX model file
    _isInitialized = true;
  }

  ObstacleType parseType(String label) {
    switch (label.toLowerCase()) {
      case 'stairs':
        return ObstacleType.stairs;
      case 'curb':
        return ObstacleType.curb;
      case 'pole':
        return ObstacleType.pole;
      case 'chair':
      case 'table':
      case 'desk':
      case 'furniture':
        return ObstacleType.furniture;
      case 'car':
      case 'vehicle':
      case 'bus':
        return ObstacleType.vehicle;
      case 'door':
        return ObstacleType.door;
      case 'person':
        return ObstacleType.person;
      case 'pothole':
        return ObstacleType.pothole;
      case 'wall':
        return ObstacleType.wall;
      default:
        return ObstacleType.unknown;
    }
  }

  String calculateDirection(double centerX) {
    if (centerX < 0.3) {
      return 'far left';
    } else if (centerX < 0.45) {
      return 'slightly left';
    } else if (centerX <= 0.55) {
      return 'directly ahead';
    } else if (centerX <= 0.7) {
      return 'slightly right';
    } else {
      return 'far right';
    }
  }

  final List<double> frameLatencyMsLog = [];

  Map<String, double> getLatencyStats() {
    if (frameLatencyMsLog.isEmpty) return {};
    final sorted = List<double>.from(frameLatencyMsLog)..sort();
    final sum = sorted.reduce((a, b) => a + b);
    final avg = sum / sorted.length;
    final minMs = sorted.first;
    final maxMs = sorted.last;
    final p95Index = ((sorted.length * 0.95).floor() - 1).clamp(0, sorted.length - 1);
    final p95Ms = sorted[p95Index];
    final fps = avg > 0 ? (1000.0 / avg) : 0.0;

    return {
      'count': sorted.length.toDouble(),
      'min_ms': minMs,
      'max_ms': maxMs,
      'avg_ms': avg,
      'p95_ms': p95Ms,
      'avg_fps': fps,
    };
  }

  /// Processes camera frame and returns detected objects
  Future<List<DetectedObject>> detectFrame(dynamic imageFrame, {bool simulation = false}) async {
    final sw = Stopwatch()..start();
    if (!_isInitialized) await initialize();

    List<DetectedObject> results = [];
    final bool useSimulation = simulation || imageFrame == null;

    if (useSimulation) {
      final now = DateTime.now().millisecondsSinceEpoch;
      final cycle = ((now / 2000).floor()) % 4;

      if (cycle == 0) {
        results = [
          DetectedObject(
            label: 'Chair',
            type: ObstacleType.furniture,
            confidence: 0.91,
            bbox: BoundingBox(left: 0.35, top: 0.4, right: 0.65, bottom: 0.8),
            distanceMeters: 1.8,
            direction: 'slightly left',
          ),
        ];
      } else if (cycle == 1) {
        results = [
          DetectedObject(
            label: 'Stairs',
            type: ObstacleType.stairs,
            confidence: 0.95,
            bbox: BoundingBox(left: 0.2, top: 0.3, right: 0.8, bottom: 0.9),
            distanceMeters: 1.2,
            direction: 'directly ahead',
          ),
        ];
      } else if (cycle == 2) {
        results = [
          DetectedObject(
            label: 'Door',
            type: ObstacleType.door,
            confidence: 0.88,
            bbox: BoundingBox(left: 0.6, top: 0.2, right: 0.9, bottom: 0.85),
            distanceMeters: 2.2,
            direction: 'slightly right',
          ),
        ];
      } else {
        results = [
          DetectedObject(
            label: 'Pole',
            type: ObstacleType.pole,
            confidence: 0.85,
            bbox: BoundingBox(left: 0.45, top: 0.1, right: 0.55, bottom: 0.9),
            distanceMeters: 1.5,
            direction: 'directly ahead',
          ),
        ];
      }
    }

    sw.stop();
    final elapsed = sw.elapsedMicroseconds / 1000.0;
    frameLatencyMsLog.add(elapsed);
    return results;
  }
}
