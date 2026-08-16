import '../vision/danger_classifier.dart';

class DropOffHazard {
  final String label;
  final double distanceMeters;
  final double dropDepthMeters;
  final DangerLevel dangerLevel;

  DropOffHazard({
    required this.label,
    required this.distanceMeters,
    required this.dropDepthMeters,
    required this.dangerLevel,
  });
}

class DepthSensorFusion {
  /// Analyzes LiDAR / ToF point cloud for sudden depth discontinuities (drop-offs, descending stairs, platform edges)
  DropOffHazard? evaluateDepthDiscontinuity(List<double> pointCloudDepthsMeters) {
    if (pointCloudDepthsMeters.length < 5) return null;

    // Compare immediate ground distance against distance 1.5m ahead
    final groundNear = pointCloudDepthsMeters[0];
    final groundAhead = pointCloudDepthsMeters[2];

    final dropDepth = groundAhead - groundNear;

    if (dropDepth > 0.45) { // 45cm sudden drop discontinuity
      print("🚨 [LiDAR DEPTH DISCONTINUITY DETECTED]: Drop-off / Descending stairs ${dropDepth.toStringAsFixed(2)}m drop at ${groundNear.toStringAsFixed(1)}m!");
      return DropOffHazard(
        label: "Descending Stairs / Drop-off",
        distanceMeters: groundNear,
        dropDepthMeters: dropDepth,
        dangerLevel: groundNear < 1.5 ? DangerLevel.high : DangerLevel.medium,
      );
    }

    return null;
  }
}
