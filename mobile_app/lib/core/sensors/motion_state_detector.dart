enum UserMotionState {
  stationary,
  walking,
  fastMoving,
}

class MotionStateDetector {
  UserMotionState _currentState = UserMotionState.walking;
  UserMotionState get currentState => _currentState;

  /// Translates accelerometer magnitude / step frequency into motion state
  UserMotionState updateMotionState(double accelMagnitude, double stepFreqHz) {
    if (accelMagnitude < 0.5 && stepFreqHz < 0.2) {
      _currentState = UserMotionState.stationary;
    } else if (accelMagnitude > 3.0 || stepFreqHz > 2.5) {
      _currentState = UserMotionState.fastMoving;
    } else {
      _currentState = UserMotionState.walking;
    }
    return _currentState;
  }

  /// Target detection frame rate (FPS) based on motion state
  int getTargetFps(UserMotionState state) {
    switch (state) {
      case UserMotionState.stationary:
        return 1;
      case UserMotionState.walking:
        return 15;
      case UserMotionState.fastMoving:
        return 22;
    }
  }
}
