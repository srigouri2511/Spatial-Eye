import 'dart:async';

enum HardwareTriggerType {
  volumeDownLongPress,
  quickBackTap,
  bluetoothHeadsetButton,
}

class HardwareTriggerListener {
  final _triggerController = StreamController<HardwareTriggerType>.broadcast();
  Stream<HardwareTriggerType> get onHardwareTrigger => _triggerController.stream;

  bool _isListening = false;

  void startListening() {
    _isListening = true;
    print("🔘 [HARDWARE TRIGGER]: Hardware button listener active (Volume Down / Headset Button / Back Tap).");
  }

  void stopListening() {
    _isListening = false;
  }

  /// Simulates hardware button trigger event
  void simulateHardwareTrigger(HardwareTriggerType type) {
    if (!_isListening) return;
    print("🔘 [HARDWARE BUTTON ACTIVATED]: ${type.name} pressed -> Triggering voice STT input directly!");
    _triggerController.add(type);
  }
}
