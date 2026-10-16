import 'api_exception.dart';

/// 页面异步状态：默认态 → 加载 → 空态 → 异常 → 无权限（skill 要求的全状态覆盖）。
sealed class AsyncState<T> {
  const AsyncState();
}

/// 默认 / 未发起。
class AsyncIdle<T> extends AsyncState<T> {
  const AsyncIdle();
}

/// 加载中。
class AsyncLoading<T> extends AsyncState<T> {
  const AsyncLoading();
}

/// 成功。`value` 为空集合时由视图渲染空态。
class AsyncData<T> extends AsyncState<T> {
  const AsyncData(this.value);

  final T value;
}

/// 失败：区分「无权限 / 需补绑 / 需实名」等 [UiAction] 分支。
class AsyncFailure<T> extends AsyncState<T> {
  const AsyncFailure(this.error);

  final ApiException error;
}

extension AsyncStateX<T> on AsyncState<T> {
  bool get isLoading => this is AsyncLoading<T>;

  bool get isFailure => this is AsyncFailure<T>;

  T? get valueOrNull => this is AsyncData<T> ? (this as AsyncData<T>).value : null;

  ApiException? get errorOrNull =>
      this is AsyncFailure<T> ? (this as AsyncFailure<T>).error : null;
}
