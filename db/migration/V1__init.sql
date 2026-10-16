-- MARS-1 · 一期 MVP · 单库 `mars_live` 初始化（Flyway）
-- 事实来源：S1a v0.4 §6.2（核心实体）+ §四（sys_config 口径）+ OP-7（4 类目）
--
-- 说明（增量范围）：
--   本文件覆盖【已实现域】的表结构与 P0.4 要求的 sys_config / live_category 初始化。
--   其余模块（moderation / replay / analytics / admin / notify / discovery 的榜单与统计表）
--   随各自模块的下一次增量追加为 V2+ 迁移；本文件保持可单独执行、可重复验证。
--
-- 统一约定（S1a v0.4 §6.3 / S2a v1.2 §2.5）：
--   * 金额一律整数分（*_fen），虚拟币整数（*_coin / coin_*），**禁止浮点列**；
--   * 时间列一律 UTC（DATETIME(3)），自然月/自然日口径在应用层按 UTC+8 切分；
--   * 资金表带 `version` 乐观锁与唯一 `biz_no`；幂等键唯一约束（绝不重复扣费）。

CREATE DATABASE IF NOT EXISTS `mars_live`
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `mars_live`;

-- ============================================================
-- 1. 配置与字典（改口径不发版）
-- ============================================================

CREATE TABLE IF NOT EXISTS `sys_config` (
  `config_key`   VARCHAR(120) NOT NULL,
  `config_value` VARCHAR(512) NOT NULL,
  `description`  VARCHAR(255) NOT NULL DEFAULT '',
  `updated_at`   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`config_key`)
) ENGINE=InnoDB COMMENT='平台口径配置（分成/费率/税率/限额/阈值）';

CREATE TABLE IF NOT EXISTS `live_category` (
  `id`                    INT          NOT NULL AUTO_INCREMENT,
  `name`                  VARCHAR(32)  NOT NULL,
  `require_qualification` TINYINT(1)   NOT NULL DEFAULT 0,
  `sort`                  INT          NOT NULL DEFAULT 0,
  `status`                VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_live_category_name` (`name`)
) ENGINE=InnoDB COMMENT='直播类目（一期 4 类，OP-7）';

-- ============================================================
-- 2. 账号与实名（FR-1 / OP-4 / OP-5 / OP-6 / OP-14 / OP-21）
-- ============================================================

CREATE TABLE IF NOT EXISTS `user` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `nickname`   VARCHAR(64)  NOT NULL DEFAULT '',
  `avatar`     VARCHAR(512) NOT NULL DEFAULT '',
  `phone_enc`  VARBINARY(256) NULL COMMENT '手机号 AES-GCM 密文',
  `phone_hash` CHAR(64)     NULL COMMENT '手机号哈希，用于唯一约束与检索',
  `status`     VARCHAR(16)  NOT NULL DEFAULT 'NORMAL',
  `created_at` DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_phone_hash` (`phone_hash`)
) ENGINE=InnoDB COMMENT='用户（手机号加密存储）';

CREATE TABLE IF NOT EXISTS `user_identity` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT      NOT NULL,
  `provider`   VARCHAR(16) NOT NULL COMMENT 'PHONE / WECHAT / APPLE',
  `open_id`    VARCHAR(128) NOT NULL,
  `union_id`   VARCHAR(128) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_identity_provider_openid` (`provider`, `open_id`),
  KEY `idx_identity_user` (`user_id`)
) ENGINE=InnoDB COMMENT='登录渠道绑定（DECISION-3/4：三渠道统一强制补绑手机号）';

CREATE TABLE IF NOT EXISTS `user_auth` (
  `user_id`          BIGINT      NOT NULL,
  `realname_verified` TINYINT(1) NOT NULL DEFAULT 0,
  `is_minor`         TINYINT(1)  NOT NULL DEFAULT 0,
  `birth_date`       DATE        NULL,
  `verify_status`    VARCHAR(16) NOT NULL DEFAULT 'UNVERIFIED' COMMENT 'UNVERIFIED/VERIFYING/VERIFIED/FAILED/LOCKED',
  `verify_channel`   VARCHAR(32) NULL COMMENT '第三方云实名供应商（OP-5，可切换）',
  `verified_at`      DATETIME(3) NULL,
  `updated_at`       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB COMMENT='实名与未成年识别（is_minor 由出生日期推导）';

CREATE TABLE IF NOT EXISTS `minor_limit_config` (
  `id`                 BIGINT      NOT NULL AUTO_INCREMENT,
  `age_from`           INT         NOT NULL,
  `age_to`             INT         NOT NULL,
  `single_limit_coin`  BIGINT      NOT NULL DEFAULT 0,
  `daily_limit_coin`   BIGINT      NOT NULL DEFAULT 0,
  `requires_realname`  TINYINT(1)  NOT NULL DEFAULT 1 COMMENT 'OP-21 开关 minor.limit_requires_realname',
  `updated_at`         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_minor_age_band` (`age_from`, `age_to`)
) ENGINE=InnoDB COMMENT='未成年打赏限额（按年龄段配置）';

-- ============================================================
-- 3. 开播 / 观看 / 关系（FR-2 / FR-3 / FR-6 最小 / OP-7 / OP-18）
-- ============================================================

CREATE TABLE IF NOT EXISTS `live_room` (
  `id`                 BIGINT       NOT NULL AUTO_INCREMENT,
  `anchor_id`          BIGINT       NOT NULL COMMENT '1 主播 1 房间',
  `title`              VARCHAR(128) NOT NULL DEFAULT '',
  `cover_url`          VARCHAR(512) NOT NULL DEFAULT '',
  `category_id`        INT          NOT NULL,
  `status`             VARCHAR(16)  NOT NULL DEFAULT 'IDLE' COMMENT 'IDLE/LIVE/ENDED',
  `current_session_id` BIGINT       NULL,
  `created_at`         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_live_room_anchor` (`anchor_id`),
  KEY `idx_live_room_status_category` (`status`, `category_id`)
) ENGINE=InnoDB COMMENT='直播间';

CREATE TABLE IF NOT EXISTS `live_session` (
  `id`           BIGINT      NOT NULL AUTO_INCREMENT,
  `room_id`      BIGINT      NOT NULL,
  `status`       VARCHAR(16) NOT NULL DEFAULT 'PREPARING' COMMENT 'IDLE/PREPARING/LIVE/ENDED/INTERRUPTED',
  `started_at`   DATETIME(3) NULL,
  `ended_at`     DATETIME(3) NULL,
  `end_reason`   VARCHAR(32) NULL,
  `peak_online`  INT         NOT NULL DEFAULT 0,
  `created_at`   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_live_session_room` (`room_id`, `status`)
) ENGINE=InnoDB COMMENT='直播场次（录制与回放按场次归属）';

CREATE TABLE IF NOT EXISTS `follow` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `follower_id` BIGINT      NOT NULL,
  `anchor_id`   BIGINT      NOT NULL,
  `status`      VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/CANCELLED（取关软删）',
  `created_at`  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_follow_pair` (`follower_id`, `anchor_id`)
) ENGINE=InnoDB COMMENT='关注关系（关注流 OP-12）';

CREATE TABLE IF NOT EXISTS `room_mute` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `room_id`     BIGINT      NOT NULL,
  `user_id`     BIGINT      NOT NULL,
  `operator_id` BIGINT      NOT NULL,
  `expire_at`   DATETIME(3) NULL,
  `reason`      VARCHAR(255) NOT NULL DEFAULT '',
  `created_at`  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_room_mute` (`room_id`, `user_id`)
) ENGINE=InnoDB COMMENT='禁言（房管仅可管理本直播间，BR-7）';

CREATE TABLE IF NOT EXISTS `room_ban` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `room_id`     BIGINT      NOT NULL,
  `user_id`     BIGINT      NOT NULL,
  `operator_id` BIGINT      NOT NULL,
  `reason`      VARCHAR(255) NOT NULL DEFAULT '',
  `created_at`  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_room_ban` (`room_id`, `user_id`)
) ENGINE=InnoDB COMMENT='踢出/房间级拉黑';

-- ============================================================
-- 4. 资金：钱包 / 币池 / 充值 / IAP（FR-5 / OP-11 / OP-15）
-- ============================================================

CREATE TABLE IF NOT EXISTS `wallet_account` (
  `user_id`   BIGINT      NOT NULL,
  `balance`   BIGINT      NOT NULL DEFAULT 0 COMMENT '虚拟币余额（单向不可提现，BR-4）',
  `version`   BIGINT      NOT NULL DEFAULT 0 COMMENT '乐观锁',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB COMMENT='虚拟币钱包';

CREATE TABLE IF NOT EXISTS `wallet_ledger` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT,
  `user_id`       BIGINT      NOT NULL,
  `biz_type`      VARCHAR(16) NOT NULL COMMENT 'RECHARGE/GIFT/REFUND/ADJUST',
  `biz_no`        VARCHAR(64) NOT NULL,
  `direction`     VARCHAR(8)  NOT NULL COMMENT 'IN/OUT',
  `amount`        BIGINT      NOT NULL,
  `balance_after` BIGINT      NOT NULL,
  `channel`       VARCHAR(16) NULL,
  `created_at`    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wallet_ledger_biz` (`biz_no`),
  KEY `idx_wallet_ledger_user` (`user_id`, `created_at`)
) ENGINE=InnoDB COMMENT='虚拟币流水（biz_no 唯一，幂等基础）';

CREATE TABLE IF NOT EXISTS `coin_pool_state` (
  `id`             INT         NOT NULL DEFAULT 1,
  `coin_pool_total` BIGINT     NOT NULL DEFAULT 0,
  `gross_pool_fen` BIGINT      NOT NULL DEFAULT 0,
  `fee_pool_fen`   BIGINT      NOT NULL DEFAULT 0,
  `version`        BIGINT      NOT NULL DEFAULT 0 COMMENT '乐观锁（充值/退款同事务更新）',
  `updated_at`     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB COMMENT='滚动累计币池（S1a §3.4.2 加权平均成本法）';

CREATE TABLE IF NOT EXISTS `coin_pool_channel` (
  `channel`    VARCHAR(16) NOT NULL COMMENT 'WECHAT/ALIPAY/IAP',
  `coins`      BIGINT      NOT NULL DEFAULT 0,
  `gross_fen`  BIGINT      NOT NULL DEFAULT 0,
  `fee_fen`    BIGINT      NOT NULL DEFAULT 0,
  `fee_rate`   DECIMAL(9,6) NOT NULL DEFAULT 0 COMMENT '下单快照费率（历史账单不受后续配置影响）',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`channel`)
) ENGINE=InnoDB COMMENT='币池渠道构成（结算渠道明细的数据来源）';

CREATE TABLE IF NOT EXISTS `recharge_order` (
  `id`                BIGINT      NOT NULL AUTO_INCREMENT,
  `order_no`          VARCHAR(64) NOT NULL,
  `user_id`           BIGINT      NOT NULL,
  `amount_fen`        BIGINT      NOT NULL COMMENT '实付金额（分）',
  `coin_amount`       BIGINT      NOT NULL COMMENT '到账币数（含赠送币）',
  `pay_channel`       VARCHAR(16) NOT NULL COMMENT 'WECHAT/ALIPAY/IAP',
  `channel_fee_rate`  DECIMAL(9,6) NOT NULL COMMENT '下单时费率快照（AC-8 可复算前提）',
  `status`            VARCHAR(16) NOT NULL DEFAULT 'CREATED' COMMENT 'CREATED/PAID/FAILED/CLOSED',
  `created_at`        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `paid_at`           DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_recharge_order_no` (`order_no`),
  KEY `idx_recharge_user` (`user_id`, `created_at`)
) ENGINE=InnoDB COMMENT='充值订单';

CREATE TABLE IF NOT EXISTS `iap_transaction` (
  `id`               BIGINT      NOT NULL AUTO_INCREMENT,
  `transaction_id`   VARCHAR(64) NOT NULL,
  `user_id`          BIGINT      NOT NULL,
  `product_id`       VARCHAR(128) NOT NULL,
  `channel_fee_rate` DECIMAL(9,6) NOT NULL COMMENT 'Apple 抽成费率快照（小企业计划变更不影响历史）',
  `env`              VARCHAR(16) NOT NULL DEFAULT 'PROD' COMMENT 'PROD/SANDBOX',
  `status`           VARCHAR(16) NOT NULL DEFAULT 'VERIFIED' COMMENT 'VERIFIED/REFUNDED/REVOKED',
  `created_at`       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_iap_transaction` (`transaction_id`),
  KEY `idx_iap_user` (`user_id`)
) ENGINE=InnoDB COMMENT='IAP 交易（仅 iOS，OP-11）';

-- ============================================================
-- 5. 礼物 / 收益 / 结算 / 提现（FR-5 / FR-10 / OP-15 / OP-16）
-- ============================================================

CREATE TABLE IF NOT EXISTS `gift` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `name`        VARCHAR(64) NOT NULL,
  `price_coin`  BIGINT      NOT NULL,
  `icon_url`    VARCHAR(512) NOT NULL DEFAULT '',
  `effect_type` VARCHAR(32) NOT NULL DEFAULT '',
  `status`      VARCHAR(16) NOT NULL DEFAULT 'ON' COMMENT 'ON/OFF（下架）',
  `sort`        INT         NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB COMMENT='礼物目录';

CREATE TABLE IF NOT EXISTS `gift_order` (
  `id`               BIGINT      NOT NULL AUTO_INCREMENT,
  `order_no`         VARCHAR(64) NOT NULL,
  `idempotency_key`  VARCHAR(64) NOT NULL COMMENT '幂等键唯一（同键重放返回首次结果，绝不重复扣费）',
  `session_id`       BIGINT      NOT NULL,
  `sender_id`        BIGINT      NOT NULL,
  `anchor_id`        BIGINT      NOT NULL,
  `gift_id`          BIGINT      NOT NULL,
  `count`            INT         NOT NULL DEFAULT 1,
  `total_coin`       BIGINT      NOT NULL,
  `status`           VARCHAR(16) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/REVERSED',
  `created_at`       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_gift_order_no` (`order_no`),
  UNIQUE KEY `uk_gift_order_idem` (`idempotency_key`),
  KEY `idx_gift_order_session` (`session_id`),
  KEY `idx_gift_order_anchor_month` (`anchor_id`, `created_at`)
) ENGINE=InnoDB COMMENT='礼物赠送单';

CREATE TABLE IF NOT EXISTS `earning_account` (
  `anchor_id`    BIGINT      NOT NULL,
  `total_earned` BIGINT      NOT NULL DEFAULT 0 COMMENT '累计收益（= Σ anchor_net）',
  `available`    BIGINT      NOT NULL DEFAULT 0 COMMENT '可提现余额（月结后转入）',
  `withdrawing`  BIGINT      NOT NULL DEFAULT 0 COMMENT '结算中（已产生未到月结时点）',
  `version`      BIGINT      NOT NULL DEFAULT 0,
  `updated_at`   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`anchor_id`)
) ENGINE=InnoDB COMMENT='主播收益账户（DB 列名 → API-DTO 名映射见契约 §5.3.1）';

CREATE TABLE IF NOT EXISTS `earning_ledger` (
  `id`             BIGINT      NOT NULL AUTO_INCREMENT,
  `anchor_id`      BIGINT      NOT NULL,
  `biz_type`       VARCHAR(16) NOT NULL COMMENT 'GIFT/SETTLEMENT/WITHDRAW/ADJUST',
  `biz_no`         VARCHAR(64) NOT NULL,
  `direction`      VARCHAR(8)  NOT NULL COMMENT 'IN/OUT（冲正为负向，不删原记录）',
  `amount`         BIGINT      NOT NULL,
  `balance_after`  BIGINT      NOT NULL DEFAULT 0,
  `created_at`     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_earning_ledger_biz` (`biz_no`),
  KEY `idx_earning_ledger_anchor` (`anchor_id`, `created_at`)
) ENGINE=InnoDB COMMENT='主播收益流水';

CREATE TABLE IF NOT EXISTS `settlement_bill` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT,
  `anchor_id`         BIGINT       NOT NULL,
  `period`            VARCHAR(7)   NOT NULL COMMENT 'UTC+8 自然月，如 2026-11',
  `share_rate`        DECIMAL(6,4) NOT NULL,
  `gross_amount`      BIGINT       NOT NULL COMMENT '记账折算收入（Σ消费币 × avg_coin_gross_fen）',
  `channel_fee`       BIGINT       NOT NULL,
  `platform_tax_fee`  BIGINT       NOT NULL COMMENT '平台流转税（先扣后分）',
  `distributable`     BIGINT       NOT NULL,
  `anchor_gross`      BIGINT       NOT NULL COMMENT 'floor(distributable × share_rate)',
  `platform_net`      BIGINT       NOT NULL COMMENT 'distributable − anchor_gross（吸收取整残差）',
  `anchor_tax`        BIGINT       NOT NULL COMMENT '主播个税（代扣代缴，OP-23）',
  `anchor_net`        BIGINT       NOT NULL COMMENT 'anchor_gross − anchor_tax',
  `adjustment_fen`    BIGINT       NOT NULL DEFAULT 0 COMMENT '历史冲正计入下一期，不改已出账账单',
  `rounding_diff_fen` BIGINT       NOT NULL DEFAULT 0 COMMENT '渠道明细取整差（计入平台侧）',
  `status`            VARCHAR(16)  NOT NULL DEFAULT 'GENERATED',
  `created_at`        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settlement_bill_period` (`anchor_id`, `period`),
  KEY `idx_settlement_period` (`period`)
) ENGINE=InnoDB COMMENT='月度结算账单（字段名与 S1a §3.4 / 契约 §4.6.1 逐字一致）';

CREATE TABLE IF NOT EXISTS `settlement_bill_channel` (
  `id`                  BIGINT       NOT NULL AUTO_INCREMENT,
  `bill_id`             BIGINT       NOT NULL,
  `channel`             VARCHAR(16)  NOT NULL,
  `coin_consumed`       BIGINT       NOT NULL,
  `coin_share_bp`       INT          NOT NULL COMMENT '池内占比（万分比，Σ=10000）',
  `gross_fen`           BIGINT       NOT NULL,
  `fee_fen`             BIGINT       NOT NULL,
  `fee_rate_snapshot`   DECIMAL(9,6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bill_channel` (`bill_id`, `channel`),
  KEY `idx_bill_channel_bill` (`bill_id`)
) ENGINE=InnoDB COMMENT='结算账单渠道明细（Σ明细 + rounding_diff_fen = 汇总 channel_fee）';

CREATE TABLE IF NOT EXISTS `withdrawal` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT,
  `order_no`      VARCHAR(64) NOT NULL,
  `anchor_id`     BIGINT      NOT NULL,
  `amount_fen`    BIGINT      NOT NULL COMMENT 'API-DTO: pre_tax_amount（契约 §5.3.1 B）',
  `tax_fen`       BIGINT      NOT NULL DEFAULT 0 COMMENT 'API-DTO: tax_withheld（仅 WITHDRAW 模式生效）',
  `status`        VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/PAID',
  `reject_reason` VARCHAR(255) NULL,
  `created_at`    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_withdrawal_order_no` (`order_no`),
  KEY `idx_withdrawal_anchor` (`anchor_id`, `created_at`)
) ENGINE=InnoDB COMMENT='提现申请（仅个人主播，起提 10000 分）';

-- ============================================================
-- 6. 初始化数据
-- ============================================================

-- 6.1 平台口径（S1a v0.4 §四；改口径不发版）
INSERT INTO `sys_config` (`config_key`, `config_value`, `description`) VALUES
  ('anchor_share_rate',           '0.5',    '主播分成率（OP-15，50/50）'),
  ('platform_tax_rate',           '0.06',   '平台流转税率（先扣后分，按订单快照）'),
  ('coin_nominal_fen',            '1',      '名义折算：1 币 = 1 分（仅展示，不得进入结算字段）'),
  ('withdraw_min_fen',            '10000',  '起提金额（分）= 100 元（OP-16）'),
  ('settlement_period',           'MONTHLY','结算周期：月结（OP-15）'),
  ('anchor_tax_mode',             'SETTLE', '个税扣缴时点：SETTLE（默认）/ WITHDRAW（OP-23）'),
  ('anchor_tax_rule',             'FLAT',   '个税规则：FLAT / PROGRESSIVE（OP-23 待人类定值）'),
  ('anchor_tax_rate',             '0.06',   'FLAT 占位费率（PENDING-OP-23，上线前必须替换）'),
  ('minor.limit_requires_realname','true',  'OP-21 开关：打赏/充值前需实名或年龄声明（默认开）'),
  ('invoice.iap_mode',            'APPLE_ONLY', 'OP-22：IAP 发票口径，默认仅非 IAP 由平台开票'),
  ('channel_fee_rate.WECHAT',     '0.006',  '微信支付渠道费率（下单快照）'),
  ('channel_fee_rate.ALIPAY',     '0.006',  '支付宝渠道费率（下单快照）'),
  ('channel_fee_rate.IAP',        '0.30',   'Apple 抽成费率（下单快照；小企业计划 15% 时新订单按新值）'),
  ('danmaku.rate_per_second',     '1',      '弹幕频控：1 条/秒（S1a 唯一口径）'),
  ('danmaku.max_chars',           '50',     '弹幕单条最大字数 ≤50'),
  ('danmaku.dedup_seconds',       '5',      '弹幕去重窗口 5 秒'),
  ('idle_stream_minutes',         '30',     '空闲自动断流阈值（OP-18）'),
  ('idle_remind_minutes',         '28',     '断流前提醒时点'),
  ('appeal_days',                 '7',      '申诉时限 7 天（OP-17）'),
  ('replay_retention_days',       '30',     '回放留存 30 天（OP-13）'),
  ('deactivate_cooling_days',     '15',     '账号注销冷却期（BR-9）'),
  ('hot.weight.w1',               '1',      '热度权重：在线人数'),
  ('hot.weight.w2',               '1',      '热度权重：近 5 分钟弹幕数'),
  ('hot.weight.w3',               '1',      '热度权重：近 5 分钟礼物币')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);

-- 6.2 一期 4 类目（OP-7）
INSERT INTO `live_category` (`id`, `name`, `require_qualification`, `sort`, `status`) VALUES
  (1, '才艺', 0, 1, 'ENABLED'),
  (2, '游戏', 0, 2, 'ENABLED'),
  (3, '闲聊', 0, 3, 'ENABLED'),
  (4, '生活', 0, 4, 'ENABLED')
ON DUPLICATE KEY UPDATE `status` = VALUES(`status`), `sort` = VALUES(`sort`);

-- 6.3 币池单例行（§3.4.2 滚动累计池）
INSERT INTO `coin_pool_state` (`id`, `coin_pool_total`, `gross_pool_fen`, `fee_pool_fen`, `version`) VALUES
  (1, 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE `id` = `id`;

-- 6.4 未成年限额默认档（OP-6；具体数值待运营配置，OP-21 另有开关）
INSERT INTO `minor_limit_config` (`age_from`, `age_to`, `single_limit_coin`, `daily_limit_coin`, `requires_realname`) VALUES
  (0, 17, 1000, 5000, 1)
ON DUPLICATE KEY UPDATE `requires_realname` = VALUES(`requires_realname`);

-- 6.5 礼物目录最小集（FR-5.2；完整目录由运营后台维护）
INSERT INTO `gift` (`id`, `name`, `price_coin`, `icon_url`, `effect_type`, `status`, `sort`) VALUES
  (1, '小心心', 1,   '', 'LIGHT',  'ON', 1),
  (2, '鲜花',   10,  '', 'LIGHT',  'ON', 2),
  (3, '火箭',   1000,'', 'ROCKET', 'ON', 3)
ON DUPLICATE KEY UPDATE `status` = VALUES(`status`);
