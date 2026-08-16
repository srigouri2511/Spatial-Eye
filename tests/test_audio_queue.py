import unittest

class PriorityAudioQueueSimulator:
    def __init__(self):
        self.queue = []
        self.currently_speaking = None
        self.current_priority = 0
        self.logs = []

    def enqueue(self, text: str, priority: int):
        # 0 = LOW, 1 = MEDIUM, 2 = HIGH
        if priority == 2 and self.currently_speaking and self.current_priority < 2:
            self.logs.append(f"INTERRUPTED: '{self.currently_speaking}' by HIGH priority '{text}'")
            self.currently_speaking = text
            self.current_priority = 2
            return

        self.queue.append((priority, text))
        self.queue.sort(key=lambda x: x[0], reverse=True)
        if not self.currently_speaking:
            self._process_next()

    def _process_next(self):
        if self.queue:
            p, text = self.queue.pop(0)
            self.currently_speaking = text
            self.current_priority = p
            self.logs.append(f"SPEAKING: '{text}' (Priority {p})")

class TestAudioPriorityQueue(unittest.TestCase):
    def test_high_priority_interrupts_low_priority(self):
        q = PriorityAudioQueueSimulator()
        q.enqueue("Chair 3 meters ahead", priority=0)  # LOW
        self.assertEqual(q.currently_speaking, "Chair 3 meters ahead")

        # Now high danger alert comes in
        q.enqueue("WARNING! Descending stairs 1 meter ahead!", priority=2)  # HIGH
        self.assertEqual(q.currently_speaking, "WARNING! Descending stairs 1 meter ahead!")
        self.assertIn("INTERRUPTED", q.logs[-1])

    def test_queue_sorting_by_priority(self):
        q = PriorityAudioQueueSimulator()
        q.enqueue("Low danger notice", priority=0)
        q.enqueue("Medium danger caution", priority=1)
        # Medium should be prioritized at the head of the pending queue
        self.assertEqual(q.queue[0][1], "Medium danger caution")

if __name__ == "__main__":
    unittest.main()
