/// 弹幕限流口径（**唯一来源 = 服务端**，契约 §2.8 + API-INT-02）。
///
/// 契约 B-1 裁定：参数为**服务端唯一来源**，UI 不另立、不覆盖、不缓存。
/// 因此本文件的值只作两类用途：
/// 1. 承载**服务端下发**的运行期配置（`sys_config`）；
/// 2. [contractDefaults] 仅作为配置尚未到达时的**首屏兜底展示**，
///    **任何一次真实发送都以服务端响应为准**（`42900` / `40001` / `deduped`）。
class DanmakuLimits {
  const DanmakuLimits({
    required this.minInterval,
    required this.maxLength,
    required this.dedupeWindow,
  });

  /// 发送间隔下限：**1 条/秒**。
  final Duration minInterval;

  /// 单条长度上限：**≤50 字**。
  final int maxLength;

  /// 内容去重窗口：**5s**。
  final Duration dedupeWindow;

  /// 契约默认口径（服务端未下发时的兜底）。
  static const DanmakuLimits contractDefaults = DanmakuLimits(
    minInterval: Duration(seconds: 1),
    maxLength: 50,
    dedupeWindow: Duration(seconds: 5),
  );

  factory DanmakuLimits.fromServerConfig(JsonMapLike config) {
    int readInt(String key, int fallback) {
      final Object? raw = config[key];
      if (raw is num) {
        return raw.toInt();
      }
      if (raw is String) {
        return int.tryParse(raw) ?? fallback;
      }
      return fallback;
    }

    return DanmakuLimits(
      minInterval: Duration(milliseconds: readInt('danmakuMinIntervalMs', 1000)),
      maxLength: readInt('danmakuMaxLength', 50),
      dedupeWindow: Duration(milliseconds: readInt('danmakuDedupeWindowMs', 5000)),
    );
  }
}

/// 便于在不引入 `Map<String, dynamic>` 别名的情况下解析配置。
typedef JsonMapLike = Map<String, Object?>;

/// 本地预检结果（**仅用于即时反馈 / 按钮禁用**，不替代服务端判定）。
enum DanmakuPrecheck {
  ok,

  /// 空内容。
  empty,

  /// 超过服务端下发的字数上限。
  tooLong,

  /// 距上次发送未满服务端下发的间隔。
  throttled,
}

/// 本地预检器：给「发送」按钮即时反馈，减少无效请求。
///
/// **不作为限流事实源**：每次真实发送仍会打到 `POST /live/rooms/{id}/danmaku`，
/// 由服务端返回 `42900`（超频）/ `40001`（超长）/ `deduped`（5s 去重）。
class DanmakuGate {
  DanmakuGate({DanmakuLimits limits = DanmakuLimits.contractDefaults})
      : _limits = limits;

  final DanmakuLimits _limits;

  DanmakuLimits get limits => _limits;

  DanmakuPrecheck evaluate(String content, {DateTime? lastSentAt, DateTime? now}) {
    final String trimmed = content.trim();
    if (trimmed.isEmpty) {
      return DanmakuPrecheck.empty;
    }
    if (trimmed.runes.length > _limits.maxLength) {
      return DanmakuPrecheck.tooLong;
    }
    final DateTime? last = lastSentAt;
    if (last != null) {
      final DateTime current = now ?? DateTime.now();
      if (current.difference(last) < _limits.minInterval) {
        return DanmakuPrecheck.throttled;
      }
    }
    return DanmakuPrecheck.ok;
  }

  /// 剩余可发送等待毫秒数（0 表示可立即发送），用于按钮倒计时。
  int remainingCooldownMs({DateTime? lastSentAt, DateTime? now}) {
    final DateTime? last = lastSentAt;
    if (last == null) {
      return 0;
    }
    final DateTime current = now ?? DateTime.now();
    final int remaining =
        _limits.minInterval.inMilliseconds - current.difference(last).inMilliseconds;
    return remaining > 0 ? remaining : 0;
  }

  String hintFor(DanmakuPrecheck precheck) => switch (precheck) {
        DanmakuPrecheck.ok => '',
        DanmakuPrecheck.empty => '请输入内容',
        DanmakuPrecheck.tooLong => '单条弹幕不超过 ${_limits.maxLength} 字',
        DanmakuPrecheck.throttled => '发送太快了，请稍后再试。',
      };
}
