import 'package:flutter/material.dart';

import '../../app_scope.dart';
import '../../core/api_exception.dart';
import '../../core/async_state.dart';
import '../../core/formatters.dart';
import '../../core/payment_policy.dart';
import '../../models/wallet.dart';
import '../../widgets/common.dart';

/// 充值页（D-VIEW-003 · FR-5.1 / OP-11）。
///
/// **iOS 仅 IAP**：iOS 端只展示 IAP 档位，且**不调用** `POST /wallet/recharge`
/// （契约 §4.6 API-ASSET-05：iOS 调用即 `40001` 平台不符）；
/// 非 iOS 端只展示微信 / 支付宝，不展示 IAP。
class RechargePage extends StatefulWidget {
  const RechargePage({super.key});

  static const String route = '/recharge';

  @override
  State<RechargePage> createState() => _RechargePageState();
}

class _RechargePageState extends State<RechargePage> {
  AsyncState<WalletInfo> _wallet = const AsyncIdle<WalletInfo>();
  List<IapProduct> _iapProducts = const <IapProduct>[];
  bool _busy = false;
  ApiException? _error;
  String? _hint;

  bool _bootstrapped = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (!_bootstrapped) {
      _bootstrapped = true;
      _load();
    }
  }

  Future<void> _load() async {
    setState(() => _wallet = const AsyncLoading<WalletInfo>());
    final AppServices services = AppScope.of(context);
    try {
      final WalletInfo wallet = await services.assets.fetchWallet();
      final List<IapProduct> products = PaymentPolicy.canUseIap(services.platform)
          ? await services.assets.fetchIapProducts()
          : const <IapProduct>[];
      if (!mounted) {
        return;
      }
      setState(() {
        _wallet = AsyncData<WalletInfo>(wallet);
        _iapProducts = products;
      });
    } on ApiException catch (error) {
      if (mounted) {
        setState(() => _wallet = AsyncFailure<WalletInfo>(error));
      }
    }
  }

  Future<void> _buyCashier(CoinPackage package, PayChannel channel) async {
    setState(() {
      _busy = true;
      _error = null;
      _hint = null;
    });
    try {
      final RechargeOrder order = await AppScope.of(context).assets.createRechargeOrder(
            packageId: package.id,
            channel: channel,
          );
      if (mounted) {
        setState(() => _hint = '已下单 ${order.orderNo}，请在三方收银台完成支付');
      }
    } on ApiException catch (error) {
      if (mounted) {
        setState(() => _error = error);
      }
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  Future<void> _buyIap(IapProduct product) async {
    setState(() {
      _busy = true;
      _error = null;
      _hint = null;
    });
    try {
      // StoreKit 2 购买后回传 JWS；此处以 mock 令牌驱动校验链路（RISK-7 spike 项）。
      await AppScope.of(context).assets.verifyIap(
            signedTransactionInfo: 'valid:${product.productId}',
          );
      await _load();
      if (mounted) {
        setState(() => _hint = '购买成功，已到账 ${product.coinAmount} 币');
      }
    } on ApiException catch (error) {
      if (mounted) {
        setState(() => _error = error);
      }
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final AppServices services = AppScope.of(context);
    final bool ios = PaymentPolicy.isIos(services.platform);

    return Scaffold(
      appBar: AppBar(title: const Text('我的钱包')),
      body: AsyncStateView<WalletInfo>(
        state: _wallet,
        onRetry: _load,
        onErrorAction: (BuildContext context, ApiException error) => _load(),
        builder: (BuildContext context, WalletInfo wallet) => ListView(
          padding: const EdgeInsets.all(16),
          children: <Widget>[
            Card(
              child: ListTile(
                title: const Text('当前余额'),
                trailing: Text(
                  Money.coin(wallet.balance, symbol: '${wallet.coinName} '),
                  style: Theme.of(context).textTheme.titleMedium,
                ),
              ),
            ),
            const SizedBox(height: 8),
            Text(
              ios ? 'iOS 端仅支持 Apple 内购（IAP），不提供微信 / 支付宝充值。' : '支持微信支付 / 支付宝。',
              style: const TextStyle(fontSize: 12),
            ),
            const SizedBox(height: 12),
            if (ios)
              ..._iapProducts.map(
                (IapProduct product) => Card(
                  child: ListTile(
                    title: Text('${product.coinAmount} ${wallet.coinName}'),
                    subtitle: Text('Apple 内购 · ${product.productId}'),
                    trailing: FilledButton(
                      onPressed: _busy ? null : () => _buyIap(product),
                      child: const Text('购买'),
                    ),
                  ),
                ),
              )
            else
              ...wallet.packagesFor(services.platform).map(
                    (CoinPackage package) => Card(
                      child: ListTile(
                        title: Text('${package.totalCoin} ${wallet.coinName}'),
                        subtitle: Text(Money.fenToYuan(package.priceFen)),
                        trailing: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: <Widget>[
                            TextButton(
                              onPressed: _busy ? null : () => _buyCashier(package, PayChannel.wechat),
                              child: const Text('微信'),
                            ),
                            TextButton(
                              onPressed: _busy ? null : () => _buyCashier(package, PayChannel.alipay),
                              child: const Text('支付宝'),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
            if (_hint != null) ...<Widget>[
              const SizedBox(height: 12),
              Text(_hint!, style: const TextStyle(fontSize: 12)),
            ],
            if (_error != null) ...<Widget>[
              const SizedBox(height: 12),
              InlineErrorText(error: _error),
            ],
          ],
        ),
      ),
    );
  }
}
