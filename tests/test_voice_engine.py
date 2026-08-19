import unittest

class VoiceCommandEngineSimulator:
    def __init__(self):
        self.is_awaiting_place_name = False

    def parse_transcript(self, transcript: str):
        text = transcript.lower().strip()

        if self.is_awaiting_place_name:
            self.is_awaiting_place_name = False
            return {"intent": "providePlaceName", "payload": transcript}

        if any(kw in text for kw in [
            "stop", "pause", "be quiet", "silence", "turn off camera",
            "off camera", "camera off", "close camera", "stop camera"
        ]):
            return {"intent": "stop", "payload": None}

        if any(kw in text for kw in [
            "open camera", "start camera", "turn on camera", "turn camera on",
            "on camera", "on the camera", "camera on", "activate camera",
            "enable camera", "camera"
        ]):
            return {"intent": "openCamera", "payload": None}

        if any(kw in text for kw in [
            "change ui to light", "light mode", "light theme",
            "switch to light mode", "turn on light mode", "light ui"
        ]):
            return {"intent": "toggleTheme", "payload": "light"}

        if any(kw in text for kw in [
            "change ui to dark", "dark mode", "dark theme",
            "switch to dark mode", "turn on dark mode", "dark ui"
        ]):
            return {"intent": "toggleTheme", "payload": "dark"}

        if any(kw in text for kw in [
            "save this place", "save location", "save room", "bookmark place"
        ]):
            return {"intent": "savePlace", "payload": None}

        if any(kw in text for kw in [
            "what's in front", "what is in front", "scan ahead", "describe",
            "what do you see", "detect objects", "check obstacles", "scan"
        ]):
            return {"intent": "querySurroundings", "payload": None}

        if any(kw in text for kw in [
            "where am i", "recognize place", "which room", "what room is this"
        ]):
            return {"intent": "whereAmI", "payload": None}

        if any(kw in text for kw in [
            "repeat", "say again", "repeat alert"
        ]):
            return {"intent": "repeatLast", "payload": None}

        if any(kw in text for kw in [
            "stop", "pause", "be quiet", "silence", "turn off camera",
            "off camera", "camera off"
        ]):
            return {"intent": "stop", "payload": None}

        return {"intent": "unknown", "payload": None}

class TestVoiceEngine(unittest.TestCase):
    def setUp(self):
        self.engine = VoiceCommandEngineSimulator()

    def test_on_camera_phrase(self):
        res = self.engine.parse_transcript("whn i on the camera")
        self.assertEqual(res["intent"], "openCamera")

    def test_on_camera_short_phrase(self):
        res = self.engine.parse_transcript("on camera")
        self.assertEqual(res["intent"], "openCamera")

    def test_change_ui_to_light(self):
        res = self.engine.parse_transcript("change ui to light")
        self.assertEqual(res["intent"], "toggleTheme")
        self.assertEqual(res["payload"], "light")

    def test_light_mode(self):
        res = self.engine.parse_transcript("switch to light mode")
        self.assertEqual(res["intent"], "toggleTheme")
        self.assertEqual(res["payload"], "light")

    def test_dark_mode(self):
        res = self.engine.parse_transcript("change ui to dark")
        self.assertEqual(res["intent"], "toggleTheme")
        self.assertEqual(res["payload"], "dark")

    def test_detect_objects_query(self):
        res = self.engine.parse_transcript("detect objects in front of me")
        self.assertEqual(res["intent"], "querySurroundings")

    def test_stop_camera(self):
        res = self.engine.parse_transcript("turn off camera")
        self.assertEqual(res["intent"], "stop")

if __name__ == "__main__":
    unittest.main()
