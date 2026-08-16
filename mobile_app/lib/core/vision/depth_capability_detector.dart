enum DepthHardwareTier {
  tier1HardwareLidar,
  tier1HardwareTof,
  tier2MonocularSoftwareFallback,
}

class DepthCapabilityDetector {
  /// Detects hardware depth sensor capabilities (LiDAR / ToF vs Software Monocular Fallback)
  Future<DepthHardwareTier> detectCapabilities({
    bool hasLidar = false,
    bool hasTof = false,
  }) async {
    if (hasLidar) {
      print("🔍 [DEPTH CAPABILITY DETECTOR]: Hardware LiDAR detected (Tier 1 Active)");
      return DepthHardwareTier.tier1HardwareLidar;
    } else if (hasTof) {
      print("🔍 [DEPTH CAPABILITY DETECTOR]: Hardware Time-of-Flight (ToF) detected (Tier 1 Active)");
      return DepthHardwareTier.tier1HardwareTof;
    } else {
      print("🔍 [DEPTH CAPABILITY DETECTOR]: Non-LiDAR device -> Initializing Tier 2 Software Monocular Depth Fallback (MiDaS-small)");
      return DepthHardwareTier.tier2MonocularSoftwareFallback;
    }
  }
}
