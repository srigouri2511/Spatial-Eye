import sys
import os
import unittest
import numpy as np
from fastapi.testclient import TestClient

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '../backend')))

from app.main import app
from app.api.places import cosine_similarity
from app.config import settings

client = TestClient(app)

class TestBackendAPI(unittest.TestCase):
    def test_health_check(self):
        response = client.get("/health")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json(), {"status": "healthy"})

    def test_root_endpoint(self):
        response = client.get("/")
        self.assertEqual(response.status_code, 200)
        self.assertIn("Spatial Eye", response.json()["service"])

    def test_cosine_similarity_identical_vectors(self):
        vec1 = [1.0, 0.0, 0.0] + [0.0] * 509
        vec2 = [1.0, 0.0, 0.0] + [0.0] * 509
        dist, conf = cosine_similarity(vec1, vec2)
        self.assertAlmostEqual(dist, 0.0, places=5)
        self.assertAlmostEqual(conf, 1.0, places=5)

    def test_cosine_similarity_orthogonal_vectors(self):
        vec1 = [1.0, 0.0] + [0.0] * 510
        vec2 = [0.0, 1.0] + [0.0] * 510
        dist, conf = cosine_similarity(vec1, vec2)
        self.assertAlmostEqual(dist, 1.0, places=5)
        self.assertAlmostEqual(conf, 0.5, places=5)

    def test_true_positive_same_room_furniture_moved(self):
        """Simulates same room scan with small layout perturbation (furniture moved)."""
        # Baseline Living Room embedding vector
        base_living_room = np.zeros(512, dtype=np.float32)
        base_living_room[0] = 0.95
        base_living_room[1] = 0.10
        base_living_room /= np.linalg.norm(base_living_room)

        # Rearranged Living Room scan (slight rotation/shift of features)
        rearranged_living_room = np.zeros(512, dtype=np.float32)
        rearranged_living_room[0] = 0.90
        rearranged_living_room[1] = 0.20
        rearranged_living_room /= np.linalg.norm(rearranged_living_room)

        dist, conf = cosine_similarity(base_living_room.tolist(), rearranged_living_room.tolist())
        # Distance (~0.05) is significantly below the 0.35 threshold -> Match succeeds!
        self.assertLess(dist, settings.VECTOR_SIMILARITY_THRESHOLD)
        self.assertGreater(conf, 0.85)

    def test_cosine_distance_boundary_cutoff(self):
        """Validates exact threshold boundary behavior at 0.34 (Match) vs 0.36 (Reject)."""
        vec_base = [1.0] + [0.0] * 511

        # Construct vector at ~0.34 distance
        # cos(theta) = 1 - 0.34 = 0.66
        vec_sub_35 = [0.66, np.sqrt(1 - 0.66**2)] + [0.0] * 510
        dist_pass, _ = cosine_similarity(vec_base, vec_sub_35)
        self.assertLess(dist_pass, 0.35)

        # Construct vector at ~0.36 distance
        # cos(theta) = 1 - 0.36 = 0.64
        vec_above_35 = [0.64, np.sqrt(1 - 0.64**2)] + [0.0] * 510
        dist_fail, _ = cosine_similarity(vec_base, vec_above_35)
        self.assertGreater(dist_fail, 0.35)

    def test_false_positive_different_room_rejection(self):
        living_room = [1.0] + [0.0] * 511
        kitchen = [0.0, 1.0] + [0.0] * 510
        
        dist, conf = cosine_similarity(living_room, kitchen)
        self.assertGreater(dist, settings.VECTOR_SIMILARITY_THRESHOLD)

    def test_save_place_invalid_embedding_dim(self):
        response = client.post(
            "/api/v1/places/save",
            json={"name": "Living Room", "embeddings": [[0.1, 0.2]]}
        )
        self.assertEqual(response.status_code, 400)
        self.assertIn("Each embedding vector must have dimension", response.json()["detail"])

if __name__ == "__main__":
    unittest.main()
