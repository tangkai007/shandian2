import 'error_codes.dart';

/// 统一响应 JSON 别名。
typedef JsonMap = Map<String, dynamic>;

/// 统一响应封装（契约 §2.2）。
///
/// ```json
/// { "code": 0, "message": "ok", "data": {}, "traceId": "..." }
/// ```
///
/// `code` 为业务判定位（前端以它分支），HTTP 状态码只反映语义。
class ApiEnvelope {
  const ApiEnvelope({
    required this.code,
    required this.message,
    this.data,
    this.traceId,
  });

  final int code;
  final String message;
  final Object? data;
  final String? traceId;

  bool get isOk => code == ErrorCodes.ok;

  factory ApiEnvelope.fromJson(JsonMap json) => ApiEnvelope(
        code: (json['code'] as num?)?.toInt() ?? ErrorCodes.systemError,
        message: (json['message'] as String?) ?? '',
        data: json['data'],
        traceId: json['traceId'] as String?,
      );

  JsonMap get dataAsMap =>
      data is JsonMap ? data! as JsonMap : const <String, dynamic>{};

  List<Object?> get dataAsList => data is List ? data! as List<Object?> : const <Object?>[];
}
