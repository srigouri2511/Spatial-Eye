import 'object_detector.dart';

enum DangerLevel {
  none,
  low,
  medium,
  high,
}

class ClassifiedDanger {
  final DetectedObject object;
  final DangerLevel level;
  final String description;
  final bool isEscalated;

  ClassifiedDanger({
    required this.object,
    required this.level,
    required this.description,
    this.isEscalated = false,
  });
}

class DangerClassifier {
  // Previous distances map to track distance reduction (user approaching obstacle)
  final Map<String, double> _previousDistances = {};

  DangerLevel evaluateDangerLevel({
    required ObstacleType type,
    required double distanceMeters,
    required bool isApproaching,
  }) {
    // High danger criteria
    if (type == ObstacleType.vehicle || type == ObstacleType.pothole) {
      if (distanceMeters < 2.5) return DangerLevel.high;
    }
    if (type == ObstacleType.stairs && distanceMeters < 2.0) {
      return DangerLevel.high;
    }
    if (distanceMeters < 1.0) {
      return DangerLevel.high;
    }
    if (isApproaching && distanceMeters < 1.5) {
      return DangerLevel.high;
    }

    // Medium danger criteria
    if (distanceMeters < 2.0) {
      return DangerLevel.medium;
    }
    if (type == ObstacleType.stairs ||
        type == ObstacleType.curb ||
        type == ObstacleType.pole ||
        type == ObstacleType.door) {
      if (distanceMeters < 3.0) return DangerLevel.medium;
    }

    // Low danger criteria
    if (distanceMeters < 4.0) {
      return DangerLevel.low;
    }

    return DangerLevel.none;
  }

  /// Evaluates detected objects and returns classified danger alerts with escalation flags
  List<ClassifiedDanger> classify(List<DetectedObject> objects) {
    if (objects.isEmpty) {
      _previousDistances.clear();
      return [];
    }

    final List<ClassifiedDanger> classified = [];

    for (var obj in objects) {
      final key = '${obj.label}_${obj.direction}';
      final prevDist = _previousDistances[key];
      final isApproaching = prevDist != null && (prevDist - obj.distanceMeters) > 0.15;
      _previousDistances[key] = obj.distanceMeters;

      final dangerLevel = evaluateDangerLevel(
        type: obj.type,
        distanceMeters: obj.distanceMeters,
        isApproaching: isApproaching,
      );

      bool isEscalated = false;
      if (prevDist != null) {
        final prevLevel = evaluateDangerLevel(
          type: obj.type,
          distanceMeters: prevDist,
          isApproaching: false,
        );
        if (dangerLevel.index > prevLevel.index) {
          isEscalated = true;
        }
      }

      final String desc =
          '${obj.label}, ${obj.distanceMeters.toStringAsFixed(1)} meters, ${obj.direction}';

      classified.add(ClassifiedDanger(
        object: obj,
        level: dangerLevel,
        description: desc,
        isEscalated: isEscalated,
      ));
    }

    // Sort by highest danger level first, then closest distance
    classified.sort((a, b) {
      final compLevel = b.level.index.compareTo(a.level.index);
      if (compLevel != 0) return compLevel;
      return a.object.distanceMeters.compareTo(b.object.distanceMeters);
    });

    return classified;
  }

  /// Generates spoken description of surroundings
  String generateSpokenSummary(List<ClassifiedDanger> dangers) {
    if (dangers.isEmpty) {
      return "Nothing detected ahead.";
    }

    final topDanger = dangers.first;
    if (topDanger.level == DangerLevel.high) {
      return "Warning! High danger! ${topDanger.description}.";
    } else if (topDanger.level == DangerLevel.medium) {
      return "Caution: ${topDanger.description}.";
    } else {
      return "Notice: ${topDanger.description}.";
    }
  }
}
