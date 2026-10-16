import '../core/api_envelope.dart';
import '../core/danmaku_limits.dart';
import '../core/json_utils.dart';
import 'transport.dart';

/// 一次 mock 请求。
class MockRequest {
  MockRequest({
    required this.method,
    required this.path,
    required this.query,
    required this.body,
    required this.headers,
    required this.pathParams,
  });

  final String method;
  final String path;
  final Map<String, dynamic> query;
  final JsonMap body;
  final Map<String, String> headers;
  final List<String> pathParams;

  String? get bearer {
    final String? raw = headers['Authorization'];
    if (raw == null || !raw.startsWith('Bearer ')) {
      return null;
    }
    return raw.substring('Bearer '.length);
  }

  Object? param(int index) =>
      index < pathParams.length ? pathParams[index] : null;

  int intQuery(String key, int fallback) => Json.int_(query[key], fallback);
}

typedef MockHandler = TransportResponse Function(MockRequest request);

class _Route {
  _Route(this.method, String pattern, this.handler)
      : regex = RegExp('^$pattern\$');

  final String method;
  final RegExp regex;
  final MockHandler handler;
}

/// 内存版后端（mock-first）。
///
/// **不是"随便返回成功"的桩**：它按 S2a v1.2 契约逐条实现关键校验，
/// 使前端能在无后端、无网络的环境下验证**错误码分支**与**限流口径**：
/// - 匿名访问受保护接口 → `40100`（**绝不复用 `40325`**，契约 §2.7 边界备注）；
/// - 已登录未绑手机号发言/打赏 → `40325`；
/// - 弹幕：≤50 字 → `40001(field=content)`；1 条/秒 → `42900`；5s 去重 → `deduped=true`；
///   敏感词 → `40001(reason=敏感词)`；
/// - iOS 调 `/wallet/recharge` → `40001`（平台不符，契约 §4.6 API-ASSET-05）；
/// - 提现低于起提额 → `40340`。
///
/// 时钟可注入（[clock]），故限流 / 去重窗口可被测试确定性驱动。
class MockTransport implements Transport {
  MockTransport({DateTime Function()? clock, DanmakuLimits? limits})
      : _clock = clock ?? DateTime.now,
        _limits = limits ?? DanmakuLimits.contractDefaults {
    _registerRoutes();
  }

  final DateTime Function() _clock;
  final DanmakuLimits _limits;
  final List<_Route> _routes = <_Route>[];

  // ---------------------------------------------------------------- mock 状态

  bool loggedIn = false;
  bool phoneBound = false;
  bool realnameVerified = false;
  bool isMinor = false;
  String phoneMasked = '';
  int walletBalance = 2860;
  int totalEarnedAmount = 166800;
  int settlingAmount = 50000;
  int availableAmount = 116800;
  int withdrawableMinAmount = 10000;
  String anchorTaxMode = 'SETTLE';

  /// 敏感词表（真实实现由服务端 `sys_config` + 内容安全服务维护）。
  Set<String> sensitiveWords = <String>{'违禁词'};

  /// 视为已失效的 access 令牌（模拟过期 / 黑名单），用于驱动静默刷新链路。
  Set<String> invalidAccessTokens = <String>{};

  /// 礼物目录单价（`gift_id -> price_coin`）。
  static const Map<int, int> _giftPrices = <int, int>{1: 10, 2: 5000, 3: 1};

  DateTime? _lastDanmakuAt;
  String? _lastDanmakuContent;
  DateTime? _lastDanmakuContentAt;
  int _danmakuSeq = 0;
  int _accessTokenSeq = 1;

  // ------------------------------------------------------------------ 路由注册

  void _registerRoutes() {
    // 认证与账号
    _on('POST', '/auth/sms/send', (MockRequest r) => _ok(<String, dynamic>{
          'expire_in': 300,
          'retry_after': 60,
        }));
    _on('POST', '/auth/login/sms', (MockRequest r) {
      loggedIn = true;
      phoneBound = true;
      phoneMasked = _maskPhone(Json.str(r.body['phone']));
      return _ok(_sessionJson(needBindPhone: false));
    });
    _on('POST', '/auth/login/wechat', (MockRequest r) {
      loggedIn = true;
      // DECISION-3/4：微信登录后未绑手机号 → 强制补绑。
      return _ok(_sessionJson(needBindPhone: !phoneBound));
    });
    _on('POST', '/auth/login/apple', (MockRequest r) {
      loggedIn = true;
      // DECISION-4：**三渠道统一**，Apple 与微信同流程。
      return _ok(_sessionJson(needBindPhone: !phoneBound));
    });
    _on('POST', '/auth/refresh', (MockRequest r) => _ok(_tokenJson()));
    _on('POST', '/auth/logout', (MockRequest r) {
      loggedIn = false;
      return _ok(null);
    });
    _on('GET', '/auth/identities', (MockRequest r) {
      final TransportResponse? denied = _requireAuth(r);
      if (denied != null) {
        return denied;
      }
      return _ok(<String, dynamic>{
        'list': <Object?>[
          <String, dynamic>{'provider': 'PHONE', 'bound': phoneBound, 'masked_id': phoneMasked},
          <String, dynamic>{'provider': 'WECHAT', 'bound': loggedIn, 'masked_id': ''},
          <String, dynamic>{'provider': 'APPLE', 'bound': false, 'masked_id': ''},
        ],
      });
    });
    _on('POST', '/auth/identities/bind', (MockRequest r) {
      final TransportResponse? denied = _requireAuth(r);
      if (denied != null) {
        return denied;
      }
      phoneBound = true;
      phoneMasked = _maskPhone(Json.str(r.body['phone']));
      return _ok(<String, dynamic>{'binded': true});
    });
    _on('POST', '/auth/realname/submit', (MockRequest r) {
      final TransportResponse? denied = _requireAuth(r);
      if (denied != null) {
        return denied;
      }
      realnameVerified = true;
      return _ok(<String, dynamic>{
        'verify_status': 'VERIFIED',
        'is_minor': isMinor,
        'verify_channel': 'TENCENT',
        'fail_reason': '',
      });
    });
    _on('GET', '/auth/realname', (MockRequest r) {
      final TransportResponse? denied = _requireAuth(r);
      if (denied != null) {
        return denied;
      }
      return _ok(<String, dynamic>{
        'verify_status': realnameVerified ? 'VERIFIED' : 'UNVERIFIED',
        'is_minor': isMinor,
        'birth_date': '',
        'verified_at': '',
      });
    });
    _on('GET', '/users/me', (MockRequest r) {
      final TransportResponse? denied = _requireAuth(r);
      if (denied != null) {
        return denied;
      }
      return _ok(_userJson());
    });

    // 发现 / 关注
    _on('GET', '/categories', (MockRequest r) => _ok(<String, dynamic>{
          'list': <Object?>[
            <String, dynamic>{'id': 1, 'name': '才艺', 'require_qualification': false, 'sort': 1},
            <String, dynamic>{'id': 2, 'name': '游戏', 'require_qualification': false, 'sort': 2},
            <String, dynamic>{'id': 3, 'name': '闲聊', 'require_qualification': false, 'sort': 3},
            <String, dynamic>{'id': 4, 'name': '生活', 'require_qualification': false, 'sort': 4},
          ],
        }));
    _on('GET', '/live/rooms', (MockRequest r) {
      final String feed = Json.str(r.query['feed'], 'hot');
      if (feed == 'following') {
        // 契约 §4.2 API-DISC-02：匿名 → 40100；已登录未绑手机号 → 40325。
        final TransportResponse? denied = _requireAuth(r);
        if (denied != null) {
          return denied;
        }
        final TransportResponse? phoneGate = _requirePhone(r);
        if (phoneGate != null) {
          return phoneGate;
        }
      }
      final Object? categoryId = r.query['categoryId'];
      final List<Object?> rooms = _rooms()
          .where((JsonMap room) =>
              categoryId == null || '${room['category_id']}' == '$categoryId')
          .cast<Object?>()
          .toList();
      return _ok(<String, dynamic>{
        'list': rooms,
        'total': rooms.length,
        'page': r.intQuery('page', 1),
        'size': r.intQuery('size', 20),
      });
    });
    _on('POST', '/anchors/([^/]+)/follow', (MockRequest r) {
      final TransportResponse? denied = _requireAuth(r);
      if (denied != null) {
        return denied;
      }
      return _ok(<String, dynamic>{'following': true, 'follower_cnt': 1284});
    });
    _on('DELETE', '/anchors/([^/]+)/follow', (MockRequest r) {
      final TransportResponse? denied = _requireAuth(r);
      if (denied != null) {
        return denied;
      }
      return _ok(<String, dynamic>{'following': false, 'follower_cnt': 1283});
    });

    // 开播（注意：`/live/rooms/mine` 必须在 `/live/rooms/([^/]+)` 之前）
    _on('GET', '/live/rooms/mine', (MockRequest r) {
      final TransportResponse? denied = _requireAuth(r);
      if (denied != null) {
        return denied;
      }
      return _ok(<String, dynamic>{
        'room': <String, dynamic>{
          'id': 'room_mine',
          'title': '我的直播间',
          'cover_url': '',
          'category_id': 1,
          'status': 'IDLE',
          'current_session_id': null,
          'live_session': null,
        },
      });
    });
    _on('PATCH', '/live/rooms/([^/]+)', (MockRequest r) {
      final TransportResponse? denied = _requireAuth(r);
      if (denied != null) {
        return denied;
      }
      final int categoryId = Json.int_(r.body['category_id'], 1);
      if (categoryId < 1 || categoryId > 4) {
        // 一期 4 类白名单（OP-7 / BR-2）。
        return _err(40001, 400, data: <String, dynamic>{'field': 'category_id'});
      }
      if (!realnameVerified) {
        return _err(40310, 403);
      }
      return _ok(<String, dynamic>{'updated': true});
    });
    _on('POST', '/live/rooms/([^/]+)/precheck', (MockRequest r) {
      final TransportResponse? denied = _requireAuth(r);
      if (denied != null) {
        return denied;
      }
      final bool realnameOk = realnameVerified;
      final List<Object?> items = <Object?>[
        <String, dynamic>{'key': 'REALNAME', 'pass': realnameOk, 'code': realnameOk ? 0 : 40310},
        <String, dynamic>{'key': 'CATEGORY_ALLOWED', 'pass': true, 'code': 0},
        <String, dynamic>{'key': 'TITLE_AUDIT', 'pass': true, 'code': 0},
        <String, dynamic>{'key': 'COVER_AUDIT', 'pass': true, 'code': 0},
      ];
      return _ok(<String, dynamic>{'pass': realnameOk, 'items': items});
    });
    _on('POST', '/live/rooms/([^/]+)/start', (MockRequest r) {
      final TransportResponse? denied = _requireAuth(r);
      if (denied != null) {
        return denied;
      }
      if (!realnameVerified) {
        return _err(40310, 403);
      }
      return _ok(<String, dynamic>{
        'push_url': 'rtmp://push.example.com/live/room_mine?sign=mock',
        'stream_name': 'room_mine',
        'expire_at': '2026-10-16T16:00:00Z',
        'session_id': 's_1',
        'record_enabled': true,
      });
    });
    _on('POST', '/live/rooms/([^/]+)/stop', (MockRequest r) {
      final TransportResponse? denied = _requireAuth(r);
      if (denied != null) {
        return denied;
      }
      return _ok(<String, dynamic>{'session_id': 's_1', 'status': 'ENDED', 'ended_at': ''});
    });

    // 观看与播放
    _on('GET', '/live/rooms/([^/]+)/play-url', (MockRequest r) {
      final String protocol = Json.str(r.query['protocol'], 'flv');
      if (protocol != 'flv' && protocol != 'hls') {
        return _err(40001, 400, data: <String, dynamic>{'field': 'protocol'});
      }
      return _ok(<String, dynamic>{
        'protocol': protocol,
        'url': 'https://play.example.com/live/room_1.$protocol?sign=mock',
        'expire_at': '2026-10-16T16:00:00Z',
        'definitions': <Object?>[
          <String, dynamic>{'name': 'HD', 'url': ''},
          <String, dynamic>{'name': 'SD', 'url': ''},
        ],
      });
    });
    _on('POST', '/live/rooms/([^/]+)/heartbeat', (MockRequest r) => _ok(<String, dynamic>{'online_cnt': 321}));
    _on('POST', '/live/rooms/([^/]+)/leave', (MockRequest r) => _ok(<String, dynamic>{'online_cnt': 320}));
    _on('GET', '/live/rooms/([^/]+)', (MockRequest r) {
      final String roomId = '${r.param(0)}';
      if (roomId == 'room_ended') {
        return _err(40330, 404);
      }
      return _ok(<String, dynamic>{
        'room_id': roomId,
        'status': 'LIVE',
        'anchor': _anchorJson(roomId),
        'title': '深夜弹唱 · 来点歌',
        'category_id': 1,
        'online_cnt': 321,
        'rank': <Object?>[
          <String, dynamic>{'user_id': 'u_2', 'nickname': '小满', 'coin': 3200},
        ],
      });
    });

    // 基础互动
    _on('POST', '/live/rooms/([^/]+)/im/usersig', (MockRequest r) {
      final TransportResponse? denied = _requireAuth(r);
      if (denied != null) {
        return denied;
      }
      return _ok(<String, dynamic>{
        'sdk_app_id': 1400000000,
        'user_id': 'u_1',
        'user_sig': 'mock-usersig',
        'room_id': '${r.param(0)}',
        'expire_at': '',
      });
    });
    _on('POST', '/live/rooms/([^/]+)/danmaku', _danmaku);
    _on('POST', '/live/rooms/([^/]+)/like', (MockRequest r) => _ok(<String, dynamic>{'pass': true, 'like_cnt': 12}));

    // 礼物与虚拟资产
    _on('GET', '/gifts', (MockRequest r) => _ok(<String, dynamic>{
          'list': <Object?>[
            <String, dynamic>{'id': 1, 'name': '小心心', 'price_coin': 10, 'icon_url': '', 'effect_type': 'LIGHT', 'status': 'ON'},
            <String, dynamic>{'id': 2, 'name': '火箭', 'price_coin': 5000, 'icon_url': '', 'effect_type': 'FULL_SCREEN', 'status': 'ON'},
            <String, dynamic>{'id': 3, 'name': '下架礼物', 'price_coin': 1, 'icon_url': '', 'effect_type': '', 'status': 'OFF'},
          ],
        }));
    _on('POST', '/gifts/send', (MockRequest r) {
      final TransportResponse? denied = _requireAuth(r);
      if (denied != null) {
        return denied;
      }
      final TransportResponse? phoneGate = _requirePhone(r);
      if (phoneGate != null) {
        return phoneGate;
      }
      // 单价**取服务端礼物目录**，不信任客户端传参（契约 §2.3）。
      final int? unitPrice = _giftPrices[Json.int_(r.body['gift_id'])];
      if (unitPrice == null) {
        return _err(40001, 400, data: <String, dynamic>{'field': 'gift_id'});
      }
      final int coin = unitPrice * Json.int_(r.body['count'], 1);
      if (isMinor && coin > 5000) {
        // AC-6：未成年超限 → 40320（限额值来自后台配置）。
        return _err(40320, 403, data: <String, dynamic>{'daily_limit_coin': 5000});
      }
      if (coin > walletBalance) {
        return _err(40001, 400, data: <String, dynamic>{'field': 'balance'});
      }
      walletBalance -= coin;
      totalEarnedAmount += coin ~/ 2;
      return _ok(<String, dynamic>{
        'order_no': 'go_${++_danmakuSeq}',
        'balance': walletBalance,
        'total_coin': coin,
        'anchor_earned': coin ~/ 2,
      });
    });
    _on('GET', '/wallet', (MockRequest r) {
      final TransportResponse? denied = _requireAuth(r);
      if (denied != null) {
        return denied;
      }
      return _ok(<String, dynamic>{
        'balance': walletBalance,
        'coin_name': '币',
        'packages': <Object?>[
          <String, dynamic>{'id': 1, 'coin_amount': 600, 'price_fen': 600, 'bonus_coin': 0, 'pay_channel': 'WECHAT'},
          <String, dynamic>{'id': 2, 'coin_amount': 3000, 'price_fen': 3000, 'bonus_coin': 0, 'pay_channel': 'ALIPAY'},
          <String, dynamic>{'id': 3, 'coin_amount': 600, 'price_fen': 600, 'bonus_coin': 0, 'pay_channel': 'IAP'},
        ],
      });
    });
    _on('POST', '/wallet/recharge', (MockRequest r) {
      final TransportResponse? denied = _requireAuth(r);
      if (denied != null) {
        return denied;
      }
      // 契约 §4.6 API-ASSET-05：iOS 端不得调用本接口 → 40001（平台不符）。
      if (Json.str(r.headers['X-Client-Platform']).toUpperCase() == 'IOS') {
        return _err(40001, 400, data: <String, dynamic>{'field': 'pay_channel'});
      }
      return _ok(<String, dynamic>{
        'order_no': 'ro_${++_danmakuSeq}',
        'amount_fen': 600,
        'coin_amount': 600,
        'pay_channel': Json.str(r.body['pay_channel'], 'WECHAT'),
        'pay_params': <String, dynamic>{'prepay_id': 'mock-prepay'},
      });
    });
    _on('GET', '/wallet/iap/products', (MockRequest r) {
      final TransportResponse? denied = _requireAuth(r);
      if (denied != null) {
        return denied;
      }
      return _ok(<String, dynamic>{
        'list': <Object?>[
          <String, dynamic>{'product_id': 'com.shandian.coin600', 'coin_amount': 600, 'price_tier': 'A', 'status': 'ON'},
        ],
      });
    });
    _on('POST', '/wallet/iap/verify', (MockRequest r) {
      final TransportResponse? denied = _requireAuth(r);
      if (denied != null) {
        return denied;
      }
      final String jws = Json.str(r.body['signed_transaction_info']);
      if (!jws.startsWith('valid:')) {
        // 验签 / 商品 / 环境不符 → 40345。
        return _err(40345, 403);
      }
      walletBalance += 600;
      return _ok(<String, dynamic>{
        'balance': walletBalance,
        'coin_amount': 600,
        'transaction_id': jws.substring('valid:'.length),
      });
    });

    // 主播数据与收益
    _on('GET', '/anchor/stats/overview', (MockRequest r) {
      final TransportResponse? denied = _requireAuth(r);
      if (denied != null) {
        return denied;
      }
      return _ok(<String, dynamic>{
        'online_peak': 321,
        'watch_duration_sec': 108000,
        'danmaku_cnt': 2048,
        'gift_coin': 86400,
        'followers_add': 128,
      });
    });
    _on('GET', '/anchor/earnings', (MockRequest r) {
      final TransportResponse? denied = _requireAuth(r);
      if (denied != null) {
        return denied;
      }
      return _ok(<String, dynamic>{
        'total_earned_amount': totalEarnedAmount,
        'settling_amount': settlingAmount,
        'available_amount': availableAmount,
        'withdrawable_min_amount': withdrawableMinAmount,
        'anchor_tax_mode': anchorTaxMode,
      });
    });
    _on('GET', '/anchor/settlements', (MockRequest r) {
      final TransportResponse? denied = _requireAuth(r);
      if (denied != null) {
        return denied;
      }
      return _ok(<String, dynamic>{
        'list': <Object?>[_settlementBillJson()],
        'total': 1,
        'page': 1,
        'size': 20,
      });
    });
    _on('POST', '/anchor/withdrawals', (MockRequest r) {
      final TransportResponse? denied = _requireAuth(r);
      if (denied != null) {
        return denied;
      }
      final int amountFen = Json.int_(r.body['amount_fen']);
      if (amountFen < withdrawableMinAmount || amountFen > availableAmount) {
        return _err(40340, 403, data: <String, dynamic>{
          'withdrawable_min_amount': withdrawableMinAmount,
          'available_amount': availableAmount,
        });
      }
      final bool taxOnWithdraw = anchorTaxMode == 'WITHDRAW';
      final int taxWithheld = taxOnWithdraw ? amountFen * 3 ~/ 100 : 0;
      availableAmount -= amountFen;
      return _ok(<String, dynamic>{
        'order_no': 'wd_${++_danmakuSeq}',
        'pre_tax_amount': amountFen,
        'tax_withheld': taxWithheld,
        'net_amount': amountFen - taxWithheld,
        'status': 'PENDING',
      });
    });
  }

  void _on(String method, String pattern, MockHandler handler) {
    _routes.add(_Route(method, pattern, handler));
  }

  // -------------------------------------------------------------------- 传输

  @override
  Future<TransportResponse> send(
    String method,
    String path, {
    Map<String, dynamic>? query,
    Object? body,
    Map<String, String>? headers,
  }) async {
    final String normalizedPath = path.split('?').first;
    for (final _Route route in _routes) {
      if (route.method != method) {
        continue;
      }
      final RegExpMatch? match = route.regex.firstMatch(normalizedPath);
      if (match == null) {
        continue;
      }
      return route.handler(
        MockRequest(
          method: method,
          path: normalizedPath,
          query: query ?? const <String, dynamic>{},
          body: body is Map
              ? body.map<String, dynamic>(
                  (Object? key, Object? value) => MapEntry<String, dynamic>('$key', value))
              : const <String, dynamic>{},
          headers: headers ?? const <String, String>{},
          pathParams: match
              .groups(List<int>.generate(match.groupCount, (int i) => i + 1))
              .map((String? group) => group ?? '')
              .toList(),
        ),
      );
    }
    return _err(50000, 500, message: 'mock: no route for $method $normalizedPath');
  }

  // ---------------------------------------------------------------- 契约校验

  /// 弹幕（API-INT-02）—— **校验顺序严格按契约 §4.5**。
  TransportResponse _danmaku(MockRequest request) {
    final TransportResponse? denied = _requireAuth(request);
    if (denied != null) {
      return denied;
    }
    final TransportResponse? phoneGate = _requirePhone(request);
    if (phoneGate != null) {
      return phoneGate;
    }

    final String content = Json.str(request.body['content']).trim();

    // 2. 长度 ≤50 字
    if (content.isEmpty || content.runes.length > _limits.maxLength) {
      return _err(40001, 400, data: <String, dynamic>{'field': 'content'});
    }

    final DateTime now = _clock();

    // 3. 频控 1 条/秒
    final DateTime? lastAt = _lastDanmakuAt;
    if (lastAt != null && now.difference(lastAt) < _limits.minInterval) {
      return _err(42900, 429);
    }

    // 4. 去重 5s（同内容→ deduped，不投递）
    final DateTime? lastContentAt = _lastDanmakuContentAt;
    if (_lastDanmakuContent == content &&
        lastContentAt != null &&
        now.difference(lastContentAt) < _limits.dedupeWindow) {
      return _ok(<String, dynamic>{'pass': true, 'deduped': true, 'server_msg_id': null});
    }

    // 5. 敏感词
    for (final String word in sensitiveWords) {
      if (word.isNotEmpty && content.contains(word)) {
        return _err(40001, 400,
            data: <String, dynamic>{'field': 'content', 'reason': '敏感词'});
      }
    }

    _lastDanmakuAt = now;
    _lastDanmakuContent = content;
    _lastDanmakuContentAt = now;
    return _ok(<String, dynamic>{
      'pass': true,
      'deduped': false,
      'server_msg_id': 'm_${++_danmakuSeq}',
    });
  }

  /// 受登录保护：匿名 → `40100`（契约 §2.7 边界备注：**不得**返回 `40325`）。
  TransportResponse? _requireAuth(MockRequest request) {
    final String? bearer = request.bearer;
    if (!loggedIn || bearer == null || invalidAccessTokens.contains(bearer)) {
      return _err(40100, 401);
    }
    return null;
  }

  /// 已登录但未绑手机号 → `40325`。
  TransportResponse? _requirePhone(MockRequest request) {
    if (!phoneBound) {
      return _err(40325, 403);
    }
    return null;
  }

  // -------------------------------------------------------------------- 夹具

  JsonMap _tokenJson() => <String, dynamic>{
        'access_token': 'mock-access-${++_accessTokenSeq}',
        'refresh_token': 'mock-refresh-$_accessTokenSeq',
        'expire_in': 7200,
      };

  JsonMap _sessionJson({required bool needBindPhone}) => <String, dynamic>{
        ..._tokenJson(),
        'user': _userJson(),
        'need_bind_phone': needBindPhone,
      };

  JsonMap _userJson() => <String, dynamic>{
        'id': 'u_1',
        'nickname': '小闪',
        'avatar': '',
        'phone_masked': phoneBound ? phoneMasked : '',
        'realname_verified': realnameVerified,
        'is_minor': isMinor,
        'roles': <Object?>['VIEWER', 'ANCHOR'],
        'anchor_id': 'a_1',
        'status': 'NORMAL',
        'minor_limit': <String, dynamic>{
          'single_limit_coin': 5000,
          'daily_limit_coin': 5000,
          'requires_realname': true,
        },
      };

  JsonMap _anchorJson(String roomId) => <String, dynamic>{
        'anchor_id': 'a_$roomId',
        'nickname': '主播·小闪',
        'avatar': '',
        'follower_cnt': 1284,
        'following': false,
      };

  List<JsonMap> _rooms() => <JsonMap>[
        <String, dynamic>{
          'room_id': 'room_1',
          'anchor': _anchorJson('1'),
          'title': '深夜弹唱 · 来点歌',
          'cover_url': '',
          'category_id': 1,
          'status': 'LIVE',
          'online_cnt': 321,
          'hot_score': 9800,
          'session_id': 's_1',
        },
        <String, dynamic>{
          'room_id': 'room_2',
          'anchor': _anchorJson('2'),
          'title': '排位上分中',
          'cover_url': '',
          'category_id': 2,
          'status': 'LIVE',
          'online_cnt': 88,
          'hot_score': 4200,
          'session_id': 's_2',
        },
      ];

  /// AC-8 断言基线（契约 §4.7 API-EARN-05 示例复算值）。
  JsonMap _settlementBillJson() => <String, dynamic>{
        'bill_id': 'bill_2026_11',
        'period': '2026-11',
        'share_rate': 0.5,
        'gross_amount': 400000,
        'channel_fee': 24450,
        'platform_tax_fee': 22533,
        'distributable': 353017,
        'anchor_gross': 176508,
        'platform_net': 176509,
        'anchor_tax': 10590,
        'anchor_net': 165918,
        'adjustment_fen': 0,
        'rounding_diff_fen': 0,
        'status': 'GENERATED',
        'channels': <Object?>[
          <String, dynamic>{'channel': 'WECHAT', 'coin_consumed': 200000, 'coin_share_bp': 5000, 'gross_fen': 200000, 'fee_fen': 1200, 'fee_rate_snapshot': 0.006},
          <String, dynamic>{'channel': 'ALIPAY', 'coin_consumed': 125000, 'coin_share_bp': 3125, 'gross_fen': 125000, 'fee_fen': 750, 'fee_rate_snapshot': 0.006},
          <String, dynamic>{'channel': 'IAP', 'coin_consumed': 75000, 'coin_share_bp': 1875, 'gross_fen': 75000, 'fee_fen': 22500, 'fee_rate_snapshot': 0.3},
        ],
      };

  String _maskPhone(String phone) {
    if (phone.length < 7) {
      return phone;
    }
    return '${phone.substring(0, 3)}****${phone.substring(phone.length - 4)}';
  }

  TransportResponse _ok(Object? data) =>
      TransportResponse.json(200, <String, dynamic>{
        'code': 0,
        'message': 'ok',
        'data': data,
        'traceId': 'mock-trace',
      });

  TransportResponse _err(int code, int httpStatus, {JsonMap? data, String message = ''}) =>
      TransportResponse.json(httpStatus, <String, dynamic>{
        'code': code,
        'message': message,
        'data': data,
        'traceId': 'mock-trace',
      });
}
