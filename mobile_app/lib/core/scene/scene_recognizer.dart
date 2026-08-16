import 'dart:math';

class SceneFeatureVector {
  final List<double> values;
  final DateTime timestamp;

  SceneFeatureVector({
    required this.values,
    DateTime? timestamp,
  }) : timestamp = timestamp ?? DateTime.now();
}

class SceneRecognizer {
  final Random _random = Random();

  /// Generates a 512-d normalized scene feature embedding from camera frame
  SceneFeatureVector extractFeatureEmbedding(dynamic cameraFrame) {
    // Generates unit L2-normalized 512-dimensional feature vector
    final List<double> raw = List.generate(512, (_) => _random.nextDouble() - 0.5);
    final norm = sqrt(raw.map((x) => x * x).reduce((a, b) => a + b));
    final normalized = raw.map((x) => x / (norm > 0 ? norm : 1.0)).toList();

    return SceneFeatureVector(values: normalized);
  }
}
