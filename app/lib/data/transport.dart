import '../core/api_envelope.dart';

/// 传输层返回：HTTP 状态 + 已解析的 JSON 体。
class TransportResponse {
  const TransportResponse({required this.statusCode, required this.body});

  final int statusCode;
  final JsonMap body;

  static TransportResponse json(int statusCode, JsonMap body) =>
      TransportResponse(statusCode: statusCode, body: body);
}

/// 传输抽象。
///
/// 真实实现 = [DioTransport]；测试与本地开发 = `MockTransport`（无需网络）。
/// 二者共用同一条 [ApiClient] 代码路径，保证「mock 先行 → 切真实」只换实现不换调用。
abstract class Transport {
  Future<TransportResponse> send(
    String method,
    String path, {
    Map<String, dynamic>? query,
    Object? body,
    Map<String, String>? headers,
  });
}
