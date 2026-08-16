enum ThermalState {
  nominal,
  fair,
  serious,
  critical,
}

class ThermalMonitor {
  ThermalState _currentState = ThermalState.nominal;
  ThermalState get currentState => _currentState;

  /// Updates thermal state and returns true if power-saving throttle is required
  bool updateThermalState(ThermalState newState) {
    final prev = _currentState;
    _currentState = newState;

    final isElevated = newState == ThermalState.serious || newState == ThermalState.critical;
    if (isElevated && prev != newState) {
      print("🔥 [THERMAL WARNING]: Thermal status escalated to ${newState.name.toUpperCase()} — Forcing 1 FPS power-saving throttle!");
    }
    return isElevated;
  }

  String getSpokenThermalWarning() {
    return "Warning: Device temperature elevated. Running in power saving mode due to heat.";
  }
}
