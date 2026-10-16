import 'api_envelope.dart';

/// 宽容 JSON 取值（服务端字段缺失 / 类型漂移时不炸页面）。
class Json {
  const Json._();

  static String str(Object? value, [String fallback = '']) {
    if (value == null) {
      return fallback;
    }
    return value is String ? value : '$value';
  }

  static String? strOrNull(Object? value) {
    if (value == null) {
      return null;
    }
    final String text = value is String ? value : '$value';
    return text.isEmpty ? null : text;
  }

  static int int_(Object? value, [int fallback = 0]) {
    if (value is num) {
      return value.toInt();
    }
    if (value is String) {
      return int.tryParse(value) ?? fallback;
    }
    return fallback;
  }

  static double double_(Object? value, [double fallback = 0]) {
    if (value is num) {
      return value.toDouble();
    }
    if (value is String) {
      return double.tryParse(value) ?? fallback;
    }
    return fallback;
  }

  static bool bool_(Object? value, [bool fallback = false]) {
    if (value is bool) {
      return value;
    }
    if (value is num) {
      return value != 0;
    }
    if (value is String) {
      final String normalized = value.toLowerCase();
      if (normalized == 'true' || normalized == '1') {
        return true;
      }
      if (normalized == 'false' || normalized == '0') {
        return false;
      }
    }
    return fallback;
  }

  static List<Object?> list(Object? value) =>
      value is List ? List<Object?>.from(value) : const <Object?>[];

  static JsonMap map(Object? value) => value is Map
      ? value.map<String, dynamic>(
          (Object? key, Object? item) => MapEntry<String, dynamic>('$key', item),
        )
      : const <String, dynamic>{};
}
