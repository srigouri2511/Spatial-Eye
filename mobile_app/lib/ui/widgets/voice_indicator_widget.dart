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
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 14),
      decoration: BoxDecoration(
        color: isAwaitingName ? Colors.deepPurple.shade900 : Colors.black87,
        borderRadius: BorderRadius.circular(30),
        border: Border.all(
          color: isAwaitingName ? Colors.purpleAccent : (isListening ? Colors.cyanAccent : Colors.grey),
          width: 2,
        ),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            isAwaitingName ? Icons.record_voice_over : Icons.mic,
            color: isAwaitingName ? Colors.purpleAccent : (isListening ? Colors.cyanAccent : Colors.grey),
            size: 26,
          ),
          const SizedBox(width: 12),
          Text(
            statusText,
            style: const TextStyle(
              color: Colors.white,
              fontSize: 16,
              fontWeight: FontWeight.bold,
            ),
          ),
        ],
      ),
    );
  }
}
