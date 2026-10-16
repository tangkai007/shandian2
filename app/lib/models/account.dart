import '../core/api_envelope.dart';
import '../core/identity.dart';
import '../core/json_utils.dart';

/// 未成年人限额配置（API-ACCT-01 `minor_limit`）。
class MinorLimit {
  const MinorLimit({
    required this.singleLimitCoin,
    required this.dailyLimitCoin,
    required this.requiresRealname,
  });

  final int singleLimitCoin;
  final int dailyLimitCoin;

  /// = OP-21 开关 `minor.limit_requires_realname`。
  final bool requiresRealname;

  static const MinorLimit none = MinorLimit(
    singleLimitCoin: 0,
    dailyLimitCoin: 0,
    requiresRealname: false,
  );

  factory MinorLimit.fromJson(JsonMap json) => MinorLimit(
        singleLimitCoin: Json.int_(json['single_limit_coin']),
        dailyLimitCoin: Json.int_(json['daily_limit_coin']),
        requiresRealname: Json.bool_(json['requires_realname']),
      );
}

/// 用户资料（API-ACCT-01 `GET /users/me`）。
class UserProfile {
  const UserProfile({
    required this.id,
    required this.nickname,
    required this.avatar,
    required this.phoneMasked,
    required this.realnameVerified,
    required this.isMinor,
    required this.roles,
    required this.status,
    required this.anchorId,
    required this.minorLimit,
  });

  final String id;
  final String nickname;
  final String avatar;
  final String? phoneMasked;
  final bool realnameVerified;
  final bool isMinor;
  final List<String> roles;
  final String status;
  final String? anchorId;
  final MinorLimit minorLimit;

  /// 是否已绑定手机号（BR-1 / OP-14）。
  bool get phoneBound => phoneMasked != null && phoneMasked!.isNotEmpty;

  bool get isAnchor => roles.contains('ANCHOR');

  factory UserProfile.fromJson(JsonMap json) => UserProfile(
        id: Json.str(json['id']),
        nickname: Json.str(json['nickname']),
        avatar: Json.str(json['avatar']),
        phoneMasked: Json.strOrNull(json['phone_masked']),
        realnameVerified: Json.bool_(json['realname_verified']),
        isMinor: Json.bool_(json['is_minor']),
        roles: Json.list(json['roles']).map((Object? e) => Json.str(e)).toList(),
        status: Json.str(json['status'], 'NORMAL'),
        anchorId: Json.strOrNull(json['anchor_id']),
        minorLimit: json['minor_limit'] == null
            ? MinorLimit.none
            : MinorLimit.fromJson(Json.map(json['minor_limit'])),
      );
}

/// 登录响应（API-AUTH-02/03/04）。
class LoginResult {
  const LoginResult({
    required this.accessToken,
    required this.refreshToken,
    required this.expireIn,
    required this.user,
    required this.needBindPhone,
  });

  final String accessToken;
  final String refreshToken;
  final int expireIn;
  final UserProfile user;

  /// **三渠道统一**：未绑手机号时为 `true`，客户端强制停留补绑页（DECISION-3/4）。
  final bool needBindPhone;

  factory LoginResult.fromJson(JsonMap json) => LoginResult(
        accessToken: Json.str(json['access_token']),
        refreshToken: Json.str(json['refresh_token']),
        expireIn: Json.int_(json['expire_in'], 7200),
        user: UserProfile.fromJson(Json.map(json['user'])),
        needBindPhone: Json.bool_(json['need_bind_phone']),
      );
}

/// 实名状态机（API-AUTH-10/11）。
enum RealnameStatus { unverified, verifying, verified, failed }

RealnameStatus parseRealnameStatus(Object? wire) {
  switch (wire is String ? wire.toUpperCase() : '') {
    case 'VERIFYING':
      return RealnameStatus.verifying;
    case 'VERIFIED':
      return RealnameStatus.verified;
    case 'FAILED':
      return RealnameStatus.failed;
    case 'UNVERIFIED':
    default:
      return RealnameStatus.unverified;
  }
}

class RealnameInfo {
  const RealnameInfo({
    required this.status,
    required this.isMinor,
    required this.birthDate,
    required this.verifiedAt,
    this.failReason,
  });

  final RealnameStatus status;
  final bool isMinor;
  final String? birthDate;
  final String? verifiedAt;
  final String? failReason;

  bool get verified => status == RealnameStatus.verified;

  factory RealnameInfo.fromJson(JsonMap json) => RealnameInfo(
        status: parseRealnameStatus(json['verify_status']),
        isMinor: Json.bool_(json['is_minor']),
        birthDate: Json.strOrNull(json['birth_date']),
        verifiedAt: Json.strOrNull(json['verified_at']),
        failReason: Json.strOrNull(json['fail_reason']),
      );
}

/// 登录方式绑定情况（API-AUTH-07）。
class AuthIdentity {
  const AuthIdentity({
    required this.provider,
    required this.bound,
    required this.maskedId,
  });

  final IdentityProvider? provider;
  final bool bound;
  final String maskedId;

  factory AuthIdentity.fromJson(JsonMap json) => AuthIdentity(
        provider: parseIdentityProvider(json['provider']),
        bound: Json.bool_(json['bound']),
        maskedId: Json.str(json['masked_id']),
      );
}
