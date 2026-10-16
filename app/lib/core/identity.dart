/// 登录身份渠道（契约 §3.1 / OP-4）。
enum IdentityProvider { phone, wechat, apple }

IdentityProvider? parseIdentityProvider(Object? wire) {
  switch (wire is String ? wire.toUpperCase() : '') {
    case 'PHONE':
      return IdentityProvider.phone;
    case 'WECHAT':
      return IdentityProvider.wechat;
    case 'APPLE':
      return IdentityProvider.apple;
    default:
      return null;
  }
}

String identityProviderToWire(IdentityProvider provider) => switch (provider) {
      IdentityProvider.phone => 'PHONE',
      IdentityProvider.wechat => 'WECHAT',
      IdentityProvider.apple => 'APPLE',
    };

String identityProviderLabel(IdentityProvider provider) => switch (provider) {
      IdentityProvider.phone => '手机号',
      IdentityProvider.wechat => '微信',
      IdentityProvider.apple => 'Apple',
    };
