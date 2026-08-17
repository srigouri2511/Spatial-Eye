import 'dart:math';
import 'dart:ui';
import 'package:google_mlkit_object_detection/google_mlkit_object_detection.dart' as mlkit;

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
  late mlkit.ObjectDetector _mlkitDetector;

  Future<void> initialize() async {
    final options = mlkit.ObjectDetectorOptions(
      mode: mlkit.DetectionMode.stream,
      classifyObjects: true,
      multipleObjects: true,
    );
    _mlkitDetector = mlkit.ObjectDetector(options: options);
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
      case 'couch':
      case 'bed':
        return ObstacleType.furniture;
      case 'car':
      case 'vehicle':
      case 'bus':
      case 'truck':
        return ObstacleType.vehicle;
      case 'door':
        return ObstacleType.door;
      case 'person':
      case 'human':
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

  /// Processes an InputImage frame and returns detected objects
  Future<List<DetectedObject>> detectFrame(dynamic imageFrame, {bool simulation = false}) async {
    final sw = Stopwatch()..start();
    if (!_isInitialized) await initialize();

    List<DetectedObject> results = [];
    
    if (simulation) {
      // Mock logic removed to force real detection
      return [];
    }

    if (imageFrame is mlkit.InputImage) {
      try {
        final mlObjects = await _mlkitDetector.processImage(imageFrame);
        
        final width = imageFrame.metadata?.size.width ?? 720.0;
        final height = imageFrame.metadata?.size.height ?? 1280.0;

        for (final obj in mlObjects) {
          String labelText = 'Unknown Object';
          double confidence = 0.5;
          ObstacleType type = ObstacleType.unknown;

          if (obj.labels.isNotEmpty) {
            final bestLabel = obj.labels.reduce((a, b) => a.confidence > b.confidence ? a : b);
            if (bestLabel.confidence >= 0.2) {
              labelText = bestLabel.text;
              confidence = bestLabel.confidence;
              type = parseType(bestLabel.text);
            }
          }

          final left = (obj.boundingBox.left / width).clamp(0.0, 1.0);
          final top = (obj.boundingBox.top / height).clamp(0.0, 1.0);
          final right = (obj.boundingBox.right / width).clamp(0.0, 1.0);
          final bottom = (obj.boundingBox.bottom / height).clamp(0.0, 1.0);
          
          final bbox = BoundingBox(left: left, top: top, right: right, bottom: bottom);
          final direction = calculateDirection(bbox.centerX);

          double dist = 5.0; 
          if (bbox.height > 0) {
            dist = (1.0 / bbox.height).clamp(0.5, 8.0);
          }

          results.add(DetectedObject(
            label: labelText,
            type: type,
            confidence: confidence,
            bbox: bbox,
            distanceMeters: dist,
            direction: direction,
          ));
        }

        // Diagnostic visual fallback
        if (mlObjects.isEmpty) {
           results.add(DetectedObject(
            label: 'Scanning...',
            type: ObstacleType.unknown,
            confidence: 0.1,
            bbox: BoundingBox(left: 0.4, top: 0.4, right: 0.6, bottom: 0.6),
            distanceMeters: 6.0,
            direction: 'directly ahead',
          ));
        }

      } catch (e) {
        results.add(DetectedObject(
          label: 'Error: ${e.toString().split('\n').first}',
          type: ObstacleType.unknown,
          confidence: 1.0,
          bbox: BoundingBox(left: 0.1, top: 0.1, right: 0.9, bottom: 0.9),
          distanceMeters: 1.0,
          direction: 'directly ahead',
        ));
      }
    }

    sw.stop();
    final elapsed = sw.elapsedMicroseconds / 1000.0;
    frameLatencyMsLog.add(elapsed);
    return results;
  }

  void dispose() {
    if (_isInitialized) {
      _mlkitDetector.close();
      _isInitialized = false;
    }
  }
}
