class AmbientNoiseMonitor {
  final double highNoiseThresholdDb;
  bool _isHighNoiseMode = false;
  bool get isHighNoiseMode => _isHighNoiseMode;

  AmbientNoiseMonitor({this.highNoiseThresholdDb = 75.0});

  /// Evaluates current ambient noise level in decibels (dB)
  bool updateDecibelLevel(double currentDb) {
    final wasHighNoise = _isHighNoiseMode;
    _isHighNoiseMode = currentDb >= highNoiseThresholdDb;

    if (_isHighNoiseMode && !wasHighNoise) {
      print("🔊 [AMBIENT NOISE WARNING]: Ambient noise $currentDb dB exceeds threshold ($highNoiseThresholdDb dB) — Disabling wake word!");
      return true; // Indicates transition to high noise mode
    }
    return false;
  }

  String getSpokenHighNoiseNotice() {
    return "Noisy environment detected — use the volume button to talk to me.";
  }
}
