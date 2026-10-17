import 'error_codes.dart';

/// 文案占位符键。
///
/// 面向用户文案（= S1b v3.2 §09，经契约 §2.7 逐行回填）。
/// 规则（契约 §2.7）：**金额 / 阈值一律来自后台配置，不得硬编码** ——
/// 因此下面带占位符的文案必须由调用方通过 `params` 传入运行期值。
class UiMessageKeys {
  const UiMessageKeys._();

  /// 40320 的「限额」占位符（来自 `sys_config`，非硬编码）。
  static const String limit = 'limit';

  /// 40340 的最低下限占位符（来自 `withdrawable_min_amount`）。
  static const String minimum = 'minimum';

  /// 40340 的当前可提现占位符（来自 `available_amount`）。
  static const String available = 'available';

  /// 50000 兜底文案中的错误码占位符。
  static const String code = 'code';
}

class UiMessages {
  const UiMessages._();

  static const String _unknownChannel = '操作未成功。系统返回了未预期的错误。请重试；若仍失败请将错误码 `{code}` 告知客服。';

  /// 未登记的其它业务失败 → 全局 Toast 兜底（S1b §09）。
  static String fallback(int code) =>
      _unknownChannel.replaceFirst('{${UiMessageKeys.code}}', '$code');

  /// 按 `code` 取用户可见文案；缺失的占位符保留原样，便于测试与排查。
  static String of(
    int code, {
    String? field,
    String? reason,
    Map<String, String> params = const <String, String>{},
  }) {
    String fill(String template) {
      var out = template;
      params.forEach((String key, String value) {
        out = out.replaceAll('{$key}', value);
      });
      return out;
    }

    switch (code) {
      case ErrorCodes.ok:
        return '';
      case ErrorCodes.invalidParam:
        // 敏感词命中：contract §2.7 备注 —— `field=content` + `reason=敏感词`。
        if (reason != null && reason.isNotEmpty) {
          return '内容不合规（$reason），请修改后重试。';
        }
        return field == null || field.isEmpty ? '提交内容不合法，请检查后重试。' : '「$field」填写不合法，请检查后重试。';
      case ErrorCodes.unauthenticated:
        return '登录状态已失效，正在为你重新登录…';
      case ErrorCodes.forbidden:
        return '无权限访问';
      case ErrorCodes.realnameRequired:
        return '开播需要先完成实名认证。这是《网络安全法》对直播开播的强制要求。完成实名后即可开播。';
      case ErrorCodes.minorLimitReached:
        return fill('本账号打赏已达上限（￥{${UiMessageKeys.limit}}）。未成年账号受打赏限额保护。需监护人验证后可继续。');
      case ErrorCodes.phoneNotBound:
        return '需绑定手机号后使用该功能';
      case ErrorCodes.resourceGone:
        return '直播已结束或不存在';
      case ErrorCodes.withdrawBelowMin:
        return fill(
          '本次提现金额未达最低限额。平台最低提现 ￥{${UiMessageKeys.minimum}}（OP-16）。'
          '当前可提现 ￥{${UiMessageKeys.available}}，请调整金额。',
        );
      case ErrorCodes.iapVerifyFailed:
        return '购买校验未通过，请重试或联系客服';
      case ErrorCodes.rewardRealnameRequired:
        return '需先完成实名或年龄声明后可使用打赏';
      case ErrorCodes.conflict:
        return '操作冲突，请刷新后重试';
      case ErrorCodes.rateLimited:
        return '发送太快了，请稍后再试。';
      case ErrorCodes.systemError:
        return fallback(code);
      default:
        return fallback(code);
    }
  }
}
