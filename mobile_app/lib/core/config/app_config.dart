class AppConfig {
  /// Base URL for FastAPI Backend.
  /// When running on physical Android/iOS devices, replace with your machine's LAN IP address.
  /// Example: 'http://192.168.1.50:8000'
  static const String apiBaseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://127.0.0.1:8000/api/v1',
  );

  static const String appName = 'Spatial Eye 2.0';
  static const String appVersion = '2.0.0';

  // Feature Flags for Phase 0 Hardening
  static const bool enableHardwareAcceleratedInference = true;
  static const bool enableAdaptiveFpsThrottling = true;
  static const bool enableThermalMonitoring = true;
  static const bool enableMultiAngleCapture = true;
  static const bool enableGeofenceFiltering = true;
}
