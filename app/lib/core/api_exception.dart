import 'api_envelope.dart';
import 'error_codes.dart';
import 'ui_messages.dart';

/// 业务失败异常：由非 0 的 `code` 构造，供 UI 按 [UiAction] 分支渲染。
class ApiException implements Exception {
  ApiException(
    this.code, {
    this.message = '',
    this.field,
    this.reason,
    this.traceId,
    this.data,
    Map<String, String> params = const <String, String>{},
  }) : params = Map<String, String>.unmodifiable(params);

  final int code;

  /// 服务端原始 `message`（**不用于渲染**，仅排查）。
  final String message;

  /// `40001` 的字段定位（契约 §2.7）。
  final String? field;

  /// `40001` 的原因（如 `敏感词`）。
  final String? reason;

  final String? traceId;
  final Object? data;

  /// 运行期占位符（限额 / 起提金额等，全部来自后台配置）。
  final Map<String, String> params;

  UiAction get action => ErrorCodeMapper.actionOf(code);

  bool get isUnauthenticated => code == ErrorCodes.unauthenticated;
  bool get isPhoneNotBound => code == ErrorCodes.phoneNotBound;
  bool get isRateLimited => code == ErrorCodes.rateLimited;

  /// 面向用户文案（S1b §09）。
  String get userMessage =>
      UiMessages.of(code, field: field, reason: reason, params: params);

  factory ApiException.fromEnvelope(
    ApiEnvelope envelope, {
    Map<String, String> params = const <String, String>{},
  }) {
    final JsonMap data = envelope.dataAsMap;
    return ApiException(
      envelope.code,
      message: envelope.message,
      field: data['field'] as String?,
      reason: data['reason'] as String?,
      traceId: envelope.traceId,
      data: envelope.data,
      params: params,
    );
  }

  @override
  String toString() =>
      'ApiException(code: $code, message: $message, field: $field, reason: $reason)';
}
