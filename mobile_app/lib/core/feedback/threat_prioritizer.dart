import '../vision/obstacle_detector.dart';

enum ThreatZone { left, center, right }
enum ThreatLevel { low, medium, critical }

class AnalyzedThreat {
  final DetectedObstacle obstacle;
  final ThreatZone zone;
  final ThreatLevel level;
  final double distanceEstimate; // 0.0 (close) to 1.0 (far)

  AnalyzedThreat({
    required this.obstacle,
    required this.zone,
    required this.level,
    required this.distanceEstimate,
  });
}

class ThreatPrioritizer {
  static AnalyzedThreat? getMostImminentThreat(List<DetectedObstacle> obstacles) {
    if (obstacles.isEmpty) return null;

    List<AnalyzedThreat> threats = obstacles.map((obs) {
      ThreatZone zone;
      if (obs.centerX < 0.33) {
        zone = ThreatZone.left;
      } else if (obs.centerX > 0.66) {
        zone = ThreatZone.right;
      } else {
        zone = ThreatZone.center;
      }

      // Proximity estimation: Derive distance from bounding box height
      // height > 40% = Critical Strike Zone < 1.5m
      double distance = 1.0 - obs.height;
      if (distance < 0.0) distance = 0.0;

      ThreatLevel level = ThreatLevel.low;
      if (obs.height > 0.4 && zone == ThreatZone.center) {
        level = ThreatLevel.critical;
      } else if (obs.height > 0.2) {
        level = ThreatLevel.medium;
      }

      return AnalyzedThreat(
        obstacle: obs,
        zone: zone,
        level: level,
        distanceEstimate: distance,
      );
    }).toList();

    // Sort by level (critical first) then distance (closer first)
    threats.sort((a, b) {
      if (a.level != b.level) {
        return b.level.index.compareTo(a.level.index);
      }
      return a.distanceEstimate.compareTo(b.distanceEstimate);
    });

    return threats.first;
  }
}
