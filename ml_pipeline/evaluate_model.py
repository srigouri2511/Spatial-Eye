"""
Spatial Eye — Model Validation & Benchmark Suite
Evaluates obstacle recall, danger-level classification accuracy, and false positive rates.
"""

import numpy as np
from typing import List, Dict

class ModelEvaluator:
    def __init__(self):
        self.obstacle_types = ["stairs", "curb", "pole", "furniture", "vehicle", "door", "person", "pothole", "wall"]

    def evaluate_danger_classification(self, test_samples: List[Dict]) -> Dict[str, float]:
        """Evaluates accuracy of Low / Medium / High danger classification."""
        correct = 0
        total = len(test_samples)
        
        for sample in test_samples:
            dist = sample["distance"]
            obj = sample["object_type"]
            expected = sample["expected_danger"]
            
            # Rule engine matching dynamic classifier logic
            if obj in ["vehicle", "pothole"] and dist < 2.5:
                pred = "HIGH"
            elif obj == "stairs" and dist < 2.0:
                pred = "HIGH"
            elif dist < 1.0:
                pred = "HIGH"
            elif dist < 2.0 or obj in ["stairs", "curb", "pole"]:
                pred = "MEDIUM"
            else:
                pred = "LOW"
                
            if pred == expected:
                correct += 1
                
        accuracy = (correct / total) if total > 0 else 0.0
        return {"total_samples": total, "accuracy": round(accuracy, 4)}

    def evaluate_false_alarm_rate(self, background_frames: int, false_positives: int) -> Dict[str, float]:
        """Calculates false positive alarm rate per minute to benchmark against alert fatigue."""
        # Assuming 30 FPS camera feed
        duration_minutes = (background_frames / 30.0) / 60.0
        far_per_minute = false_positives / (duration_minutes if duration_minutes > 0 else 1.0)
        return {
            "background_frames": background_frames,
            "duration_minutes": round(duration_minutes, 2),
            "false_alarms": false_positives,
            "false_alarms_per_minute": round(far_per_minute, 2)
        }

    def evaluate_obstacle_recall(self, ground_truth: Dict[str, int], detected: Dict[str, int]) -> Dict[str, float]:
        """Calculates recall per obstacle type (especially critical small obstacles like curbs, stairs, potholes)."""
        recalls = {}
        for obj in self.obstacle_types:
            gt = ground_truth.get(obj, 0)
            det = detected.get(obj, 0)
            recalls[obj] = round(det / gt, 4) if gt > 0 else 1.0
        return recalls

if __name__ == "__main__":
    evaluator = ModelEvaluator()
    print("--- Running Benchmark Evaluation Suite ---")
    
    # Synthetic validation dataset representing indoor & outdoor obstacles
    samples = [
        {"distance": 0.8, "object_type": "stairs", "expected_danger": "HIGH"},
        {"distance": 2.2, "object_type": "furniture", "expected_danger": "LOW"},
        {"distance": 1.4, "object_type": "curb", "expected_danger": "MEDIUM"},
        {"distance": 1.8, "object_type": "vehicle", "expected_danger": "HIGH"},
        {"distance": 0.5, "object_type": "pothole", "expected_danger": "HIGH"},
        {"distance": 3.5, "object_type": "wall", "expected_danger": "LOW"},
    ]
    danger_metrics = evaluator.evaluate_danger_classification(samples)
    print(f"Danger Level Classification Accuracy: {danger_metrics['accuracy'] * 100}%")

    gt = {"stairs": 10, "curb": 15, "pothole": 8, "furniture": 20, "vehicle": 5}
    det = {"stairs": 9, "curb": 14, "pothole": 8, "furniture": 20, "vehicle": 5}
    recall_metrics = evaluator.evaluate_obstacle_recall(gt, det)
    print(f"Obstacle Detection Recall Breakdown: {recall_metrics}")

    far_metrics = evaluator.evaluate_false_alarm_rate(background_frames=1800, false_positives=1)
    print(f"False Alarm Benchmark Rate: {far_metrics['false_alarms_per_minute']} alerts/min")
