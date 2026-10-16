/// 会话令牌（access 2h / refresh 30d，刷新即轮换 —— 契约 §2.3）。
///
/// 一期实现为内存态；接入 `flutter_secure_storage` 的持久化留待 P0 真机 spike
/// 一并落地（见 impl-spec「已知问题」）。
class TokenStore {
  String? _accessToken;
  String? _refreshToken;
  DateTime? _expiresAt;

  String? get accessToken => _accessToken;

  String? get refreshToken => _refreshToken;

  bool get hasSession => _accessToken != null;

  bool get isAccessExpired {
    final DateTime? expiry = _expiresAt;
    return expiry == null || !DateTime.now().isBefore(expiry);
  }

  void save({
    required String accessToken,
    required String refreshToken,
    int? expireInSeconds,
    DateTime? now,
  }) {
    _accessToken = accessToken;
    _refreshToken = refreshToken;
    if (expireInSeconds != null && expireInSeconds > 0) {
      _expiresAt = (now ?? DateTime.now()).add(Duration(seconds: expireInSeconds));
    } else {
      _expiresAt = null;
    }
  }

  void clear() {
    _accessToken = null;
    _refreshToken = null;
    _expiresAt = null;
  }
}
