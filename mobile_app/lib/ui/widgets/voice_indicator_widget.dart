import 'package:flutter/material.dart';

class VoiceIndicatorWidget extends StatelessWidget {
  final bool isListening;
  final bool isAwaitingName;
  final String statusText;

  const VoiceIndicatorWidget({
    Key? key,
    required this.isListening,
    required this.isAwaitingName,
    required this.statusText,
  }) : super(key: key);

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final backgroundColor = isAwaitingName
        ? (isDark ? Colors.deepPurple.shade900 : Colors.purple.shade50)
        : (isDark ? Colors.black80 : Colors.white);
    final borderColor = isAwaitingName
        ? Colors.purpleAccent
        : (isListening ? (isDark ? Colors.cyanAccent : Colors.blueAccent) : Colors.grey);
    final textColor = isDark ? Colors.white : Colors.black80;

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 14),
      decoration: BoxDecoration(
        color: backgroundColor,
        borderRadius: BorderRadius.circular(30),
        border: Border.all(
          color: borderColor,
          width: 2,
        ),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(isDark ? 0.2 : 0.05),
            blurRadius: 8,
            spreadRadius: 1,
          )
        ],
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            isAwaitingName ? Icons.record_voice_over : Icons.mic,
            color: borderColor,
            size: 26,
          ),
          const SizedBox(width: 12),
          Text(
            statusText,
            style: TextStyle(
              color: textColor,
              fontSize: 16,
              fontWeight: FontWeight.bold,
            ),
          ),
        ],
      ),
    );
  }
}
