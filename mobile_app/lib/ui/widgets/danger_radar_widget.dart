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
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final hasHighDanger = classifiedDangers.any((d) => d.level == DangerLevel.high);
    final topDanger = classifiedDangers.isNotEmpty ? classifiedDangers.first : null;
    final textColor = isDark ? Colors.white : Colors.black80;
    final secondaryTextColor = isDark ? Colors.white70 : Colors.black54;

    return Semantics(
      label: topDanger != null ? topDanger.description : "Path clear",
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: isDark ? Colors.black.withOpacity(0.85) : Colors.white,
          borderRadius: BorderRadius.circular(20),
          border: Border.all(
            color: hasHighDanger
                ? Colors.red
                : (isDark ? Colors.cyan.withOpacity(0.5) : Colors.blue.withOpacity(0.4)),
            width: hasHighDanger ? 3 : 1.5,
          ),
          boxShadow: hasHighDanger
              ? [
                  BoxShadow(
                    color: Colors.red.withOpacity(0.6),
                    blurRadius: 20,
                    spreadRadius: 2,
                  )
                ]
              : [
                  BoxShadow(
                    color: Colors.black.withOpacity(isDark ? 0.3 : 0.05),
                    blurRadius: 10,
                    spreadRadius: 1,
                  )
                ],
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
                      color: topDanger != null ? _getDangerColor(topDanger.level) : Colors.green,
                      size: 28,
                    ),
                    const SizedBox(width: 10),
                    Text(
                      topDanger != null ? topDanger.level.name.toUpperCase() + " DANGER" : "PATH CLEAR",
                      style: TextStyle(
                        color: topDanger != null ? _getDangerColor(topDanger.level) : (isDark ? Colors.greenAccent : Colors.green.shade700),
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
                    color: isDark ? Colors.white10 : Colors.grey.shade200,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Text(
                    "${classifiedDangers.length} Obstacle(s)",
                    style: TextStyle(color: secondaryTextColor, fontSize: 12, fontWeight: FontWeight.bold),
                  ),
                )
              ],
            ),
            const SizedBox(height: 12),
            if (classifiedDangers.isEmpty)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 12),
                child: Text(
                  "Nothing detected ahead",
                  style: TextStyle(color: secondaryTextColor, fontSize: 18, fontStyle: FontStyle.italic),
                ),
              )
            else
              Column(
                children: classifiedDangers.map((d) {
                  return Container(
                    margin: const EdgeInsets.only(bottom: 8),
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      color: _getDangerColor(d.level).withOpacity(isDark ? 0.15 : 0.12),
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: Row(
                      children: [
                        Icon(Icons.warning, color: _getDangerColor(d.level), size: 20),
                        const SizedBox(width: 10),
                        Expanded(
                          child: Text(
                            d.description,
                            style: TextStyle(
                              color: textColor,
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
