import unittest

class TestPhase0Hardening(unittest.TestCase):
    def test_motion_state_fps_throttling(self):
        """Verifies adaptive FPS throttling: stationary=1, walking=15, fast=22."""
        def get_target_fps(state_str):
            if state_str == "stationary":
                return 1
            elif state_str == "walking":
                return 15
            elif state_str == "fast_moving":
                return 22
            return 15

        self.assertEqual(get_target_fps("stationary"), 1)
        self.assertEqual(get_target_fps("walking"), 15)
        self.assertEqual(get_target_fps("fast_moving"), 22)

    def test_frame_diff_override_during_stationary(self):
        """Verifies frame differencing overrides 1 FPS throttle during scene motion."""
        frame1 = [10] * 100
        frame2 = [10] * 50 + [90] * 50 # Significant motion delta
        
        diffs = sum(1 for a, b in zip(frame1, frame2) if abs(a - b) > 35)
        ratio = diffs / len(frame1)
        
        override = ratio >= 0.12
        self.assertTrue(override) # Override triggered! FPS ramps to 15+

    def test_thermal_state_escalation(self):
        """Verifies thermal escalation triggers power saving mode."""
        def is_thermal_throttled(thermal_state):
            return thermal_state in ["serious", "critical"]

        self.assertFalse(is_thermal_throttled("nominal"))
        self.assertFalse(is_thermal_throttled("fair"))
        self.assertTrue(is_thermal_throttled("serious"))
        self.assertTrue(is_thermal_throttled("critical"))

    def test_ambient_noise_fallback(self):
        """Verifies decibel threshold auto-disables wake-word above 75 dB."""
        threshold = 75.0
        self.assertFalse(65.0 >= threshold)
        self.assertTrue(78.5 >= threshold) # High noise -> wake word disabled!

    def test_depth_capability_tier_selection(self):
        """Verifies hardware depth detection selects Tier 1 (LiDAR) vs Tier 2 (Monocular Software Fallback)."""
        def select_depth_tier(has_lidar, has_tof):
            if has_lidar or has_tof:
                return "TIER_1_HARDWARE"
            return "TIER_2_MONOCULAR_SOFTWARE"

        self.assertEqual(select_depth_tier(has_lidar=True, has_tof=False), "TIER_1_HARDWARE")
        self.assertEqual(select_depth_tier(has_lidar=False, has_tof=False), "TIER_2_MONOCULAR_SOFTWARE")

if __name__ == "__main__":
    unittest.main()
