class GeofenceFilter {
  final String? currentWifiSsid;
  final double? currentGpsLat;
  final double? currentGpsLng;

  GeofenceFilter({
    this.currentWifiSsid,
    this.currentGpsLat,
    this.currentGpsLng,
  });

  bool isIndoorMode() {
    return currentWifiSsid != null && currentWifiSsid!.isNotEmpty;
  }

  bool isOutdoorGpsAvailable() {
    return currentGpsLat != null && currentGpsLng != null;
  }

  Map<String, dynamic> toQueryPayload() {
    final payload = <String, dynamic>{};
    if (isIndoorMode()) {
      payload['wifi_ssid'] = currentWifiSsid;
    }
    if (isOutdoorGpsAvailable()) {
      payload['gps_lat'] = currentGpsLat;
      payload['gps_lng'] = currentGpsLng;
    }
    return payload;
  }
}
