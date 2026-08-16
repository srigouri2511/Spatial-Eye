import 'dart:math';

class FrameDiffMotionOverride {
  List<int>? _prevFrameBytes;
  final double thresholdPercentage;

  FrameDiffMotionOverride({this.thresholdPercentage = 0.12});

  /// Evaluates scene motion between consecutive downsampled frames
  bool detectSceneMotion(List<int> currentFrameBytes) {
    if (_prevFrameBytes == null || _prevFrameBytes!.length != currentFrameBytes.length) {
      _prevFrameBytes = List<int>.from(currentFrameBytes);
      return false;
    }

    int diffCount = 0;
    final total = currentFrameBytes.length;

    for (int i = 0; i < total; i += 4) { // Step size 4 for high performance
      final diff = (currentFrameBytes[i] - _prevFrameBytes![i]).abs();
      if (diff > 35) { // Pixel value change threshold
        diffCount++;
      }
    }

    _prevFrameBytes = List<int>.from(currentFrameBytes);
    final ratio = diffCount / (total / 4);

    final bool hasSceneMotion = ratio >= thresholdPercentage;
    if (hasSceneMotion) {
      print("⚡ [FRAME DIFF OVERRIDE]: Significant scene motion detected (${(ratio * 100).toStringAsFixed(1)}%) — Ramping FPS to 15+!");
    }
    return hasSceneMotion;
  }
}
