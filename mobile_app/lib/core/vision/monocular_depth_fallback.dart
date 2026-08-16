import '../vision/danger_classifier.dart';

class MonocularDepthFallback {
  int _consecutiveDropOffFrames = 0;

  /// Runs on-device monocular depth model (MiDaS-small TFLite) to detect negative space on non-LiDAR devices
  ClassifiedDanger? evaluateMonocularDepth(List<double> relativeDepthMap) {
    if (relativeDepthMap.isEmpty) return null;

    // Relative depth gradient check
    final nearVal = relativeDepthMap.first;
    final farVal = relativeDepthMap.last;
    final gradient = farVal - nearVal;

    if (gradient > 0.6) { // Monocular depth gradient discontinuity
      _consecutiveDropOffFrames++;
      print("👁️ [MONOCULAR DEPTH FALLBACK (Tier 2)]: Gradient $gradient detected (Frame count: $_consecutiveDropOffFrames)");

      // Require 2 consecutive frames to escalate to High danger (avoids false alarms)
      final dangerLvl = _consecutiveDropOffFrames >= 2 ? DangerLevel.high : DangerLevel.medium;

      return ClassifiedDanger(
        object: DetectedObject(
          label: "Possible Drop-off / Stairs",
          type: ObstacleType.stairs,
          confidence: 0.82,
          bbox: BoundingBox(left: 0.2, top: 0.6, right: 0.8, bottom: 1.0),
          distanceMeters: 1.4,
          direction: "directly ahead",
        ),
        level: dangerLvl,
        description: "Caution: Monocular depth detects possible drop-off ahead",
      );
    } else {
      _consecutiveDropOffFrames = 0;
    }

    return null;
  }
}
