# app · 瞬间直播（MARS-1 一期 MVP 客户端）

Flutter **单 App 双角色**（主播端 / 观众端）。技术基线见 S1a v0.4；UI 见 S1b v3.2；接口见 S2a v1.2。

## 目录

```
lib/
  core/        错误码与分支、统一文案、限流口径、支付策略、登录门禁、格式化、TokenStore
  models/      account / live / interaction / wallet / earning（结算字段冻结于此）
  data/        transport（DioTransport / MockTransport）、ApiClient、各域 Repository
  features/    auth / host / viewer / wallet / earn 页面
  widgets/     五态视图与通用反馈
test/          错误码分支、登录门禁、支付策略、弹幕全链路、结算 AC-8、IAP、格式化、组件冒烟
```

## 运行与验证

```bash
flutter pub get
flutter analyze
flutter test
```

> **注意**：本分支落盘时**运行环境未安装 Flutter / Dart 工具链**，
> 上述命令**尚未执行**，`lib/` 与 `test/` 为未经编译验证的源码。
> 请在具备 Flutter SDK（Dart ≥ 3.3）的环境复跑；CI 见 G2.5。

## 后端对接

默认注入 `MockTransport`（`lib/data/mock_transport.dart`），它按契约实现关键校验，
可在无后端时验证错误码分支与限流口径。接入真实网关：

```dart
AppServices(
  platform: detectRuntimePlatform(),
  transport: DioTransport(baseUrl: 'https://<host>/api/v1'),
)
```

`MockTransport` 与 `DioTransport` 共用同一条 `ApiClient` 代码路径，切换只换实现。

## 未接入的原生能力（RISK-7 spike 项）

- 推流：腾讯云 MLVB `TXLivePusher`（开播页当前展示签名推流地址占位）
- 播放：腾讯云 MLVB `TXLivePlayer`（直播间当前为占位面板）
- 内购：StoreKit 2（充值页当前以 mock JWS 驱动 `POST /wallet/iap/verify` 链路）
- IM 弹幕投递通道（当前只做「本地回显 + 服务端校验」两段）

## 契约口径自检

| 口径 | 实现位置 |
| --- | --- |
| 弹幕 1 条/秒 · ≤50 字 · 5s 去重 | `lib/core/danmaku_limits.dart`、`lib/data/mock_transport.dart` |
| `40100`（未登录）/ `40325`（已登录未绑手机号） | `lib/core/error_codes.dart`、`lib/core/auth_gate.dart` |
| iOS 仅 IAP | `lib/core/payment_policy.dart`、`lib/data/asset_repository.dart` |
| 三渠道强制补绑手机号 | `lib/core/auth_gate.dart`、`lib/features/auth/bind_phone_page.dart` |
| 结算字段名 | `lib/models/earning.dart` |
