// Removed dart:ui
import 'package:flutter/material.dart';
import '../../core/vision/danger_classifier.dart';

class DangerRadarWidget extends StatelessWidget {
  final List<ClassifiedDanger> classifiedDangers;

  const DangerRadarWidget({
    Key? key,
    required this.classifiedDangers,
  }) : super(key: key);

  Color _getDangerColor(DangerLevel level) {
    switch (level) {
      case DangerLevel.high:
        return Colors.redAccent;
      case DangerLevel.medium:
        return Colors.orangeAccent;
      case DangerLevel.low:
        return Colors.yellowAccent;
      case DangerLevel.none:
        return Colors.greenAccent;
    }
  }

  @override
  Widget build(BuildContext context) {
    final hasHighDanger = classifiedDangers.any((d) => d.level == DangerLevel.high);
    final topDanger = classifiedDangers.isNotEmpty ? classifiedDangers.first : null;

    return Semantics(
      label: topDanger != null ? topDanger.description : "Path clear",
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: Colors.transparent, // Completely transparent!
          borderRadius: BorderRadius.circular(16),
              border: Border.all(
                color: hasHighDanger ? Colors.redAccent.withOpacity(0.8) : Colors.cyanAccent.withOpacity(0.3),
                width: hasHighDanger ? 2.5 : 1.0,
              ),
              boxShadow: hasHighDanger
                  ? [
                      BoxShadow(
                        color: Colors.red.withOpacity(0.3),
                        blurRadius: 30,
                        spreadRadius: 5,
                      )
                    ]
                  : [],
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Row(
                  children: [
                    Icon(
                      hasHighDanger ? Icons.warning_amber_rounded : Icons.radar,
                      color: topDanger != null ? _getDangerColor(topDanger.level) : Colors.greenAccent,
                      size: 28,
                    ),
                    const SizedBox(width: 10),
                    Text(
                      topDanger != null ? topDanger.level.name.toUpperCase() + " DANGER" : "PATH CLEAR",
                      style: TextStyle(
                        color: topDanger != null ? _getDangerColor(topDanger.level) : Colors.greenAccent,
                        fontSize: 20,
                        fontWeight: FontWeight.bold,
                        letterSpacing: 1.2,
                      ),
                    ),
                  ],
                ),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(
                    color: Colors.white10,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Text(
                    "${classifiedDangers.length} Obstacle(s)",
                    style: const TextStyle(color: Colors.white70, fontSize: 12),
                  ),
                )
              ],
            ),
            const SizedBox(height: 12),
            if (classifiedDangers.isEmpty)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 12),
                child: Text(
                  "Nothing detected ahead",
                  style: TextStyle(color: Colors.white70, fontSize: 18, fontStyle: FontStyle.italic),
                ),
              )
            else
              Column(
                children: classifiedDangers.map((d) {
                  return Container(
                    margin: const EdgeInsets.only(bottom: 8),
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      color: _getDangerColor(d.level).withOpacity(0.15),
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: Row(
                      children: [
                        Icon(Icons.warning, color: _getDangerColor(d.level), size: 20),
                        const SizedBox(width: 10),
                        Expanded(
                          child: Text(
                            d.description,
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 16,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ),
                      ],
                    ),
                  );
                }).toList(),
              ),
          ],
        ),
      ),
    );
  }
}
