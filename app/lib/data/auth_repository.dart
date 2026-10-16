import '../core/api_envelope.dart';
import '../core/json_utils.dart';
import '../core/payment_policy.dart';
import '../models/account.dart';
import 'api_client.dart';

/// 认证与账号域（契约 §3.1：API-AUTH-01…11、API-ACCT-01）。
class AuthRepository {
  AuthRepository(this._client);

  final ApiClient _client;

  ApiClient get client => _client;

  /// API-AUTH-01。
  Future<void> sendSmsCode({required String phone, String scene = 'LOGIN'}) async {
    await _client.post(
      '/auth/sms/send',
      body: <String, dynamic>{'phone': phone, 'scene': scene},
      authenticated: false,
    );
  }

  /// API-AUTH-02。
  Future<LoginResult> loginWithSms({
    required String phone,
    required String code,
    required ClientPlatform platform,
    String deviceId = '',
    String appVersion = '',
  }) =>
      _login('/auth/login/sms', <String, dynamic>{
        'phone': phone,
        'code': code,
        'device': _device(platform, deviceId, appVersion),
      });

  /// API-AUTH-03。App 只传 `code`，服务端换票（不得直传 openid）。
  Future<LoginResult> loginWithWechat({
    required String code,
    required ClientPlatform platform,
    String deviceId = '',
  }) =>
      _login('/auth/login/wechat', <String, dynamic>{
        'code': code,
        'device': _device(platform, deviceId, ''),
      });

  /// API-AUTH-04。**Apple 与微信同流程**（DECISION-4：三渠道统一强制补绑）。
  Future<LoginResult> loginWithApple({
    required String identityToken,
    required String authorizationCode,
    required ClientPlatform platform,
    String fullName = '',
    String email = '',
    String deviceId = '',
  }) =>
      _login('/auth/login/apple', <String, dynamic>{
        'identity_token': identityToken,
        'authorization_code': authorizationCode,
        'full_name': fullName,
        'email': email,
        'device': _device(platform, deviceId, ''),
      });

  /// API-AUTH-08：补绑手机号（补绑页唯一出口）。
  Future<void> bindPhone({required String phone, required String smsCode}) async {
    await _client.post('/auth/identities/bind', body: <String, dynamic>{
      'provider': 'PHONE',
      'phone': phone,
      'sms_code': smsCode,
    });
  }

  /// API-AUTH-07。
  Future<List<AuthIdentity>> fetchIdentities() async {
    final Object? data = await _client.get('/auth/identities');
    return Json.list(Json.map(data)['list'])
        .map((Object? e) => AuthIdentity.fromJson(Json.map(e)))
        .toList();
  }

  /// API-AUTH-10。
  Future<RealnameInfo> submitRealname({
    required String realName,
    required String idCardNo,
    required String birthDate,
  }) async {
    final Object? data = await _client.post('/auth/realname/submit', body: <String, dynamic>{
      'real_name': realName,
      'id_card_no': idCardNo,
      'birth_date': birthDate,
    });
    return RealnameInfo.fromJson(Json.map(data));
  }

  /// API-AUTH-11。
  Future<RealnameInfo> fetchRealname() async {
    final Object? data = await _client.get('/auth/realname');
    return RealnameInfo.fromJson(Json.map(data));
  }

  /// API-ACCT-01。
  Future<UserProfile> fetchMe() async {
    final Object? data = await _client.get('/users/me');
    return UserProfile.fromJson(Json.map(data));
  }

  /// API-AUTH-06。
  Future<void> logout() async {
    await _client.post('/auth/logout');
    _client.tokens.clear();
  }

  Future<LoginResult> _login(String path, JsonMap body) async {
    final Object? data = await _client.post(path, body: body, authenticated: false);
    final LoginResult result = LoginResult.fromJson(Json.map(data));
    _client.tokens.save(
      accessToken: result.accessToken,
      refreshToken: result.refreshToken,
      expireInSeconds: result.expireIn,
    );
    return result;
  }

  JsonMap _device(ClientPlatform platform, String deviceId, String appVersion) =>
      <String, dynamic>{
        'device_id': deviceId,
        'platform': switch (platform) {
          ClientPlatform.ios => 'IOS',
          ClientPlatform.h5 => 'H5',
          ClientPlatform.android => 'ANDROID',
        },
        'app_version': appVersion,
      };
}
