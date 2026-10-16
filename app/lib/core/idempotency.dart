/// 幂等键生成（契约 §2.6：资金类接口必填 `Idempotency-Key`）。
///
/// 客户端只需保证**重试同一次操作时复用同一个键**；服务端以业务唯一键落库。
class IdempotencyKeys {
  IdempotencyKeys._();

  static int _seq = 0;

  /// 形如 `gift-send-1699999999999-3`。同一操作重试请复用返回值，不要重新生成。
  static String next(String scope) =>
      '$scope-${DateTime.now().microsecondsSinceEpoch}-${++_seq}';
}
