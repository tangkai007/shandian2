import 'package:dio/dio.dart';

import '../core/api_envelope.dart';
import 'transport.dart';

/// 真实网络传输（Dio）。
///
/// `validateStatus` 全放行：业务失败由响应体 `code` 判定（契约 §2.2），
/// 因此 4xx / 5xx 也必须走到 [ApiEnvelope] 解析。
class DioTransport implements Transport {
  DioTransport({required this.baseUrl, Dio? dio})
      : _dio = dio ??
            Dio(
              BaseOptions(
                connectTimeout: const Duration(seconds: 10),
                receiveTimeout: const Duration(seconds: 15),
                sendTimeout: const Duration(seconds: 15),
              ),
            );

  /// 形如 `https://api.example.com/api/v1`（契约 §2.1）。
  final String baseUrl;

  final Dio _dio;

  @override
  Future<TransportResponse> send(
    String method,
    String path, {
    Map<String, dynamic>? query,
    Object? body,
    Map<String, String>? headers,
  }) async {
    final Response<dynamic> response = await _dio.request<dynamic>(
      '$baseUrl$path',
      data: body,
      queryParameters: query,
      options: Options(
        method: method,
        headers: headers,
        validateStatus: (int? status) => status != null && status < 600,
      ),
    );
    final Object? payload = response.data;
    final JsonMap json = payload is Map
        ? payload.map<String, dynamic>(
            (Object? key, Object? value) => MapEntry<String, dynamic>('$key', value),
          )
        : <String, dynamic>{
            'code': 50000,
            'message': 'unparsable response body',
          };
    return TransportResponse(statusCode: response.statusCode ?? 0, body: json);
  }
}
