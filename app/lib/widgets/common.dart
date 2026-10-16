import 'package:flutter/material.dart';

import '../core/async_state.dart';
import '../core/error_codes.dart';
import '../core/api_exception.dart';

/// 加载态。
class LoadingView extends StatelessWidget {
  const LoadingView({super.key});

  @override
  Widget build(BuildContext context) =>
      const Center(child: CircularProgressIndicator());
}

/// 空态。
class EmptyView extends StatelessWidget {
  const EmptyView({super.key, required this.message, this.action});

  final String message;
  final Widget? action;

  @override
  Widget build(BuildContext context) => Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: <Widget>[
              Text(message, textAlign: TextAlign.center),
              if (action != null) ...<Widget>[const SizedBox(height: 12), action!],
            ],
          ),
        ),
      );
}

/// 异常 / 无权限态：按 [ApiException.action] 给出主操作（S1b §09「呈现位置」）。
class FailureView extends StatelessWidget {
  const FailureView({
    super.key,
    required this.error,
    this.onRetry,
    this.onAction,
    this.actionLabelOverride,
  });

  final ApiException error;
  final VoidCallback? onRetry;
  final VoidCallback? onAction;
  final String? actionLabelOverride;

  static String? actionLabelFor(int code) => switch (ErrorCodeMapper.actionOf(code)) {
        UiAction.silentRefreshOrLogin => '去登录',
        UiAction.goBindPhone => '去绑定手机号',
        UiAction.goRealname => '去实名认证',
        UiAction.guardianVerify => '前往监护人验证',
        UiAction.realnameBeforeReward => '去实名认证',
        UiAction.roomGone => '返回',
        _ => null,
      };

  @override
  Widget build(BuildContext context) {
    final String? label = actionLabelOverride ?? actionLabelFor(error.code);
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            Text(error.userMessage, textAlign: TextAlign.center),
            const SizedBox(height: 16),
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: <Widget>[
                if (onRetry != null)
                  OutlinedButton(onPressed: onRetry, child: const Text('重试')),
                if (onRetry != null && label != null) const SizedBox(width: 12),
                if (label != null && onAction != null)
                  FilledButton(onPressed: onAction, child: Text(label)),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

/// 统一渲染 idel / loading / failure / empty / data 五态。
class AsyncStateView<T> extends StatelessWidget {
  const AsyncStateView({
    super.key,
    required this.state,
    required this.builder,
    this.emptyMessage = '暂无内容',
    this.onRetry,
    this.onErrorAction,
    this.onEmptyAction,
  });

  final AsyncState<T> state;
  final Widget Function(BuildContext context, T value) builder;
  final String emptyMessage;
  final VoidCallback? onRetry;

  /// 失败态主操作（如「去绑定手机号」）——由页面注入路由。
  final void Function(BuildContext context, ApiException error)? onErrorAction;
  final VoidCallback? onEmptyAction;

  @override
  Widget build(BuildContext context) {
    final AsyncState<T> current = state;
    if (current is AsyncIdle<T> || current is AsyncLoading<T>) {
      return const LoadingView();
    }
    if (current is AsyncFailure<T>) {
      final ApiException error = current.error;
      return FailureView(
        error: error,
        onRetry: onRetry,
        onAction: onErrorAction == null ? null : () => onErrorAction!(context, error),
      );
    }
    final T value = (current as AsyncData<T>).value;
    if (value is List && value.isEmpty) {
      return EmptyView(
        message: emptyMessage,
        action: onEmptyAction == null
            ? null
            : FilledButton(onPressed: onEmptyAction, child: const Text('刷新')),
      );
    }
    return builder(context, value);
  }
}

/// 行内错误条（40001 / 40340 等「表单字段行内」呈现位置）。
class InlineErrorText extends StatelessWidget {
  const InlineErrorText({super.key, required this.error});

  final ApiException? error;

  @override
  Widget build(BuildContext context) {
    final ApiException? current = error;
    if (current == null) {
      return const SizedBox.shrink();
    }
    return Padding(
      padding: const EdgeInsets.only(top: 8),
      child: Text(
        current.userMessage,
        style: TextStyle(color: Theme.of(context).colorScheme.error, fontSize: 12),
      ),
    );
  }
}

/// 把业务失败渲染成底部提示（Toast 落点，契约 §2.7「呈现位置」）。
void showErrorSnackBar(BuildContext context, ApiException error) {
  ScaffoldMessenger.maybeOf(context)?.showSnackBar(
    SnackBar(
      content: Text(error.userMessage.isEmpty ? '操作未成功（$error）' : error.userMessage),
      duration: const Duration(milliseconds: 2500),
    ),
  );
}
