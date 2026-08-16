import 'dart:async';
import 'scene_recognizer.dart';

class MultiAngleCaptureResult {
  final String placeName;
  final List<List<double>> embeddings;

  MultiAngleCaptureResult({
    required this.placeName,
    required this.embeddings,
  });
}

class MultiAngleCaptureFlow {
  final SceneRecognizer sceneRecognizer;

  MultiAngleCaptureFlow({required this.sceneRecognizer});

  /// Captures 3 to 5 multi-angle feature vectors while user slowly pans camera
  Future<MultiAngleCaptureResult> startCapture({
    required String placeName,
    int targetAngles = 4,
    Function(int current, int total)? onProgress,
  }) async {
    final List<List<double>> capturedVectors = [];

    print("📸 [MULTI-ANGLE CAPTURE]: Prompts user to pan phone across room...");
    for (int i = 0; i < targetAngles; i++) {
      if (onProgress != null) onProgress(i + 1, targetAngles);

      // Extract 512-d feature vector at different angles
      final vec = sceneRecognizer.extractFeatureEmbedding(null);
      capturedVectors.add(vec.values);

      // Short delay between angle captures
      await Future.delayed(const Duration(milliseconds: 600));
    }

    print("✅ [MULTI-ANGLE CAPTURE SUCCESS]: Captured ${capturedVectors.length} vectors for '$placeName'");
    return MultiAngleCaptureResult(
      placeName: placeName,
      embeddings: capturedVectors,
    );
  }
}
