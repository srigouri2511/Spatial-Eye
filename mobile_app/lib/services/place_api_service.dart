import 'dart:convert';
import 'dart:async';
import 'package:http/http.dart' as http;
import '../core/config/app_config.dart';

class SavedPlaceResult {
  final String id;
  final String name;
  final double? confidence;
  final DateTime createdAt;

  SavedPlaceResult({
    required this.id,
    required this.name,
    this.confidence,
    required this.createdAt,
  });

  factory SavedPlaceResult.fromJson(Map<String, dynamic> json) {
    return SavedPlaceResult(
      id: json['id'] ?? '',
      name: json['name'] ?? '',
      confidence: json['confidence'] != null ? (json['confidence'] as num).toDouble() : null,
      createdAt: json['created_at'] != null
          ? DateTime.parse(json['created_at'])
          : DateTime.now(),
    );
  }
}

class PlaceApiService {
  final String baseUrl;
  final http.Client _client;

  PlaceApiService({
    String? baseUrl,
    http.Client? client,
  })  : baseUrl = baseUrl ?? AppConfig.apiBaseUrl,
        _client = client ?? http.Client();

  /// Asynchronously saves place to FastAPI backend with automatic retry logic
  Future<SavedPlaceResult> savePlace({
    required String name,
    required List<List<double>> embeddings,
    String? wifiSsid,
    double? gpsLat,
    double? gpsLng,
    Map<String, dynamic>? metadata,
    int maxRetries = 3,
  }) async {
    final body = jsonEncode({
      "name": name,
      "embeddings": embeddings,
      "wifi_ssid": wifiSsid,
      "gps_lat": gpsLat,
      "gps_lng": gpsLng,
      "metadata": metadata ?? {},
    });

    int attempts = 0;
    while (attempts < maxRetries) {
      attempts++;
      try {
        final response = await _client
            .post(
              Uri.parse("$baseUrl/places/save"),
              headers: {"Content-Type": "application/json"},
              body: body,
            )
            .timeout(const Duration(seconds: 5));

        if (response.statusCode == 201) {
          final data = jsonDecode(response.body);
          print("✅ [BACKEND SAVE SUCCESS]: Saved '$name' (ID: ${data['id']})");
          return SavedPlaceResult.fromJson(data);
        }
      } catch (e) {
        print("⚠️ [BACKEND SAVE RETRY]: Attempt $attempts failed ($e). Retrying...");
        if (attempts >= maxRetries) rethrow;
        await Future.delayed(Duration(milliseconds: 500 * attempts));
      }
    }
    throw Exception("Failed to save place after $maxRetries attempts");
  }

  /// Recognizes current scene against saved places in PostgreSQL vector database
  Future<List<SavedPlaceResult>> recognizePlace({
    required List<double> embedding,
    int topK = 1,
  }) async {
    try {
      final response = await _client
          .post(
            Uri.parse("$baseUrl/places/recognize"),
            headers: {"Content-Type": "application/json"},
            body: jsonEncode({
              "embedding": embedding,
              "top_k": topK,
            }),
          )
          .timeout(const Duration(seconds: 4));

      if (response.statusCode == 200) {
        final List list = jsonDecode(response.body);
        return list.map((item) => SavedPlaceResult.fromJson(item)).toList();
      }
    } catch (e) {
      print("⚠️ [BACKEND RECOGNIZE NOTICE]: Offline or fallback matching mode ($e)");
    }
    return [];
  }
}
