import 'api_envelope.dart';
import 'json_utils.dart';

/// 分页响应（契约 §2.4）：`{ "list": [], "total": 0, "page": 1, "size": 20 }`。
class PageResult<T> {
  const PageResult({
    required this.list,
    required this.total,
    required this.page,
    required this.size,
  });

  final List<T> list;
  final int total;
  final int page;
  final int size;

  bool get isEmpty => list.isEmpty;

  static PageResult<T> fromJson<T>(JsonMap json, T Function(JsonMap item) parse) =>
      PageResult<T>(
        list: Json.list(json['list'])
            .map((Object? e) => parse(Json.map(e)))
            .toList(),
        total: Json.int_(json['total']),
        page: Json.int_(json['page'], 1),
        size: Json.int_(json['size'], 20),
      );
}
