import '../core/api_envelope.dart';
import '../core/api_exception.dart';
import '../core/error_codes.dart';
import '../core/json_utils.dart';
import '../core/token_store.dart';
import 'transport.dart';

/// 统一 API 客户端（契约 §2 通用约定）。
///
/// 职责：
/// - 注入 `Authorization: Bearer`（§2.3）与 `Idempotency-Key`（§2.6，资金类必填）；
/// - 解析统一响应封装，`code != 0` → 抛 [ApiException]（供 UI 按 [UiAction] 分支）；
/// - `40100` 时**最多静默刷新一次**令牌并重放（轮换语义，§2.3）；
///   刷新失败则清空会话并抛出 `40100`，由 UI 跳登录。
class ApiClient {
  ApiClient({required Transport transport, TokenStore? tokens})
      : _transport = transport,
        _tokens = tokens ?? TokenStore();

  final Transport _transport;
  final TokenStore _tokens;

  TokenStore get tokens => _tokens;

  Future<Object?> get(
    String path, {
    Map<String, dynamic>? query,
    Map<String, String>? params,
    Map<String, String>? headers,
    bool authenticated = true,
  }) =>
      _request('GET', path,
          query: query,
          params: params,
          headers: headers,
          authenticated: authenticated);

  Future<Object?> post(
    String path, {
    Object? body,
    Map<String, String>? params,
    Map<String, String>? headers,
    String? idempotencyKey,
    bool authenticated = true,
  }) =>
      _request('POST', path,
          body: body,
          params: params,
          headers: headers,
          idempotencyKey: idempotencyKey,
          authenticated: authenticated);

  Future<Object?> patch(
    String path, {
    Object? body,
    Map<String, String>? params,
    Map<String, String>? headers,
    bool authenticated = true,
  }) =>
      _request('PATCH', path,
          body: body,
          params: params,
          headers: headers,
          authenticated: authenticated);

  Future<Object?> delete(
    String path, {
    Map<String, dynamic>? query,
    Map<String, String>? params,
    Map<String, String>? headers,
    bool authenticated = true,
  }) =>
      _request('DELETE', path,
          query: query,
          params: params,
          headers: headers,
          authenticated: authenticated);

  Future<Object?> _request(
    String method,
    String path, {
    Map<String, dynamic>? query,
    Object? body,
    Map<String, String>? params,
    Map<String, String>? headers,
    String? idempotencyKey,
    bool authenticated = true,
    bool allowRefresh = true,
  }) async {
    final Map<String, String> requestHeaders = <String, String>{
      'Content-Type': 'application/json; charset=utf-8',
      'Accept': 'application/json',
      ...?headers,
    };
    final String? accessToken = _tokens.accessToken;
    if (authenticated && accessToken != null) {
      requestHeaders['Authorization'] = 'Bearer $accessToken';
    }
    if (idempotencyKey != null) {
      requestHeaders['Idempotency-Key'] = idempotencyKey;
    }

    final TransportResponse response = await _transport.send(
      method,
      path,
      query: query,
      body: body,
      headers: requestHeaders,
    );
    final ApiEnvelope envelope = ApiEnvelope.fromJson(response.body);

    if (envelope.isOk) {
      return envelope.data;
    }

    // 令牌失效：静默刷新一次并重放。刷新接口自身与匿名接口不参与重放，避免死循环。
    final bool refreshable = allowRefresh &&
        envelope.code == ErrorCodes.unauthenticated &&
        _tokens.refreshToken != null &&
        !path.startsWith('/auth/');
    if (refreshable) {
      final bool refreshed = await _refresh();
      if (refreshed) {
        return _request(
          method,
          path,
          query: query,
          body: body,
          params: params,
          headers: headers,
          idempotencyKey: idempotencyKey,
          authenticated: authenticated,
          allowRefresh: false,
        );
      }
    }

    throw ApiException.fromEnvelope(
      envelope,
      params: params ?? const <String, String>{},
    );
  }

  Future<bool> _refresh() async {
    final String? refreshToken = _tokens.refreshToken;
    if (refreshToken == null) {
      return false;
    }
    final TransportResponse response = await _transport.send(
      'POST',
      '/auth/refresh',
      body: <String, dynamic>{'refresh_token': refreshToken},
      headers: const <String, String>{'Content-Type': 'application/json'},
    );
    final ApiEnvelope envelope = ApiEnvelope.fromJson(response.body);
    if (!envelope.isOk) {
      _tokens.clear();
      return false;
    }
    final JsonMap data = envelope.dataAsMap;
    _tokens.save(
      accessToken: Json.str(data['access_token']),
      refreshToken: Json.str(data['refresh_token']),
      expireInSeconds: Json.int_(data['expire_in'], 7200),
    );
    return true;
  }
}
