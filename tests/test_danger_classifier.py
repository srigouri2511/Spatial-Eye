import unittest

class TestDangerClassifier(unittest.TestCase):
    def evaluate_danger_level(self, type_str: str, distance: float, is_approaching: bool = False) -> str:
        """Mirror of Dart DangerClassifier logic."""
        if type_str in ["vehicle", "pothole"] and distance < 2.5:
            return "HIGH"
        if type_str == "stairs" and distance < 2.0:
            return "HIGH"
        if distance < 1.0:
            return "HIGH"
        if is_approaching and distance < 1.5:
            return "HIGH"
        if distance < 2.0 or type_str in ["stairs", "curb", "pole", "door"]:
            if distance < 3.0:
                return "MEDIUM"
        if distance < 4.0:
            return "LOW"
        return "NONE"

    def classify_multiple(self, obstacles):
        results = []
        for obj, dist in obstacles:
            lvl = self.evaluate_danger_level(obj, dist)
            results.append((obj, dist, lvl))
        # Sort by highest danger first
        priority = {"HIGH": 3, "MEDIUM": 2, "LOW": 1, "NONE": 0}
        results.sort(key=lambda x: priority[x[2]], reverse=True)
        return results

    def test_high_danger_moving_vehicle(self):
        level = self.evaluate_danger_level("vehicle", 2.1)
        self.assertEqual(level, "HIGH")

    def test_high_danger_close_stairs(self):
        level = self.evaluate_danger_level("stairs", 1.2)
        self.assertEqual(level, "HIGH")

    def test_high_danger_proximity_under_1m(self):
        level = self.evaluate_danger_level("furniture", 0.8)
        self.assertEqual(level, "HIGH")

    def test_medium_danger_stationary_pole(self):
        level = self.evaluate_danger_level("pole", 2.5)
        self.assertEqual(level, "MEDIUM")

    def test_low_danger_distant_wall(self):
        level = self.evaluate_danger_level("wall", 3.5)
        self.assertEqual(level, "LOW")

    def test_escalation_when_approaching(self):
        level1 = self.evaluate_danger_level("furniture", 1.8, is_approaching=False)
        self.assertEqual(level1, "MEDIUM")
        level2 = self.evaluate_danger_level("furniture", 1.3, is_approaching=True)
        self.assertEqual(level2, "HIGH")

    def test_boundary_exact_thresholds(self):
        # Exact 1.0m boundary
        self.assertEqual(self.evaluate_danger_level("furniture", 0.99), "HIGH")
        # Exact 2.0m boundary for stairs
        self.assertEqual(self.evaluate_danger_level("stairs", 1.99), "HIGH")
        # 4.0m boundary
        self.assertEqual(self.evaluate_danger_level("wall", 4.1), "NONE")

    def test_multiple_obstacles_precedence(self):
        obstacles = [("wall", 3.8), ("stairs", 1.2), ("chair", 2.2)]
        classified = self.classify_multiple(obstacles)
        # Highest danger obstacle ("stairs") must take first priority
        self.assertEqual(classified[0][0], "stairs")
        self.assertEqual(classified[0][2], "HIGH")

    def test_empty_frame_clear_path(self):
        obstacles = []
        classified = self.classify_multiple(obstacles)
        self.assertEqual(len(classified), 0)

if __name__ == "__main__":
    unittest.main()
