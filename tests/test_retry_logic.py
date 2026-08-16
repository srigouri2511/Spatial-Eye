import unittest
import time
from typing import Dict, Any

class MockHttpClient:
    def __init__(self, fail_count: int):
        self.fail_count = fail_count
        self.attempts = 0
        self.timestamps = []

    def post(self, url: str, json_body: Dict[str, Any]):
        self.attempts += 1
        self.timestamps.append(time.time())
        if self.attempts <= self.fail_count:
            raise Exception(f"Simulated network drop on attempt {self.attempts}")
        return {"status_code": 201, "body": {"id": "place-uuid-123", "name": json_body.get("name")}}

class PlaceApiServiceSimulator:
    def __init__(self, client: MockHttpClient):
        self.client = client

    def save_place(self, name: str, embedding: list, max_retries: int = 3):
        attempts = 0
        while attempts < max_retries:
            attempts += 1
            try:
                res = self.client.post("/api/v1/places/save", {"name": name, "embedding": embedding})
                if res["status_code"] == 201:
                    return {"success": True, "data": res["body"], "attempts": attempts}
            except Exception as e:
                if attempts >= max_retries:
                    return {"success": False, "error": str(e), "attempts": attempts}
                time.sleep(0.05 * attempts) # Backoff delay simulation

class TestRetryLogic(unittest.TestCase):
    def test_retry_3_times_then_succeeds(self):
        """Fails first 2 attempts, succeeds on 3rd attempt."""
        client = MockHttpClient(fail_count=2)
        service = PlaceApiServiceSimulator(client)
        
        result = service.save_place("Living Room", [[0.1] * 512], max_retries=3)
        
        self.assertTrue(result["success"])
        self.assertEqual(result["attempts"], 3)
        self.assertEqual(client.attempts, 3)
        self.assertEqual(result["data"]["name"], "Living Room")

    def test_all_retries_fail_surfaces_error(self):
        """Fails all 3 attempts and returns clear error response without hanging."""
        client = MockHttpClient(fail_count=5)
        service = PlaceApiServiceSimulator(client)

        result = service.save_place("Office", [[0.1] * 512], max_retries=3)

        self.assertFalse(result["success"])
        self.assertEqual(result["attempts"], 3)
        self.assertIn("Simulated network drop", result["error"])

if __name__ == "__main__":
    unittest.main()
