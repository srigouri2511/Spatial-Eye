import 'package:flutter_test/flutter_test.dart';
import 'package:spatial_eye/core/vision/obstacle_detector.dart';
import 'package:spatial_eye/core/feedback/threat_prioritizer.dart';

void main() {
  group('ThreatPrioritizer Tests', () {
    test('Empty list returns null', () {
      final result = ThreatPrioritizer.getMostImminentThreat([]);
      expect(result, isNull);
    });

    test('Center object with high height is Critical', () {
      final obs = DetectedObstacle(
        label: 'Person',
        confidence: 0.9,
        left: 0.4,
        top: 0.1,
        right: 0.6,
        bottom: 0.9, // height = 0.8
      );
      final result = ThreatPrioritizer.getMostImminentThreat([obs]);
      
      expect(result, isNotNull);
      expect(result!.level, ThreatLevel.critical);
      expect(result.zone, ThreatZone.center);
    });

    test('Left object is mapped to Left Zone', () {
      final obs = DetectedObstacle(
        label: 'Chair',
        confidence: 0.8,
        left: 0.0,
        top: 0.5,
        right: 0.2,
        bottom: 0.8, // height = 0.3
      );
      final result = ThreatPrioritizer.getMostImminentThreat([obs]);
      
      expect(result, isNotNull);
      expect(result!.level, ThreatLevel.medium);
      expect(result.zone, ThreatZone.left);
    });
    
    test('Prioritizes Critical over Medium', () {
      final criticalObs = DetectedObstacle(
        label: 'Wall',
        confidence: 0.9,
        left: 0.4,
        top: 0.1,
        right: 0.6,
        bottom: 0.9, // height = 0.8, Center
      );
      final mediumObs = DetectedObstacle(
        label: 'Chair',
        confidence: 0.8,
        left: 0.0,
        top: 0.5,
        right: 0.2,
        bottom: 0.8, // height = 0.3, Left
      );
      
      final result = ThreatPrioritizer.getMostImminentThreat([mediumObs, criticalObs]);
      
      expect(result, isNotNull);
      expect(result!.obstacle.label, 'Wall');
      expect(result.level, ThreatLevel.critical);
    });
  });
}
