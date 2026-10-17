-- =====================================================================
-- V4 · 后台运营台补齐（增量 5 · 契约 §3.9 API-ADM-01…29）
--
-- 范围：用户封禁备注、类目受限位、运营位（banner）、发票、实名核验记录扩展、
--       提现打款/银行卡字段、刷新令牌黑名单。
-- 说明：本文件只做「列/表补齐」，不改动 V1…V3 既有列语义；字段名与契约 §4.9
--       及前端 admin/src/types/contract.ts 逐字对齐。
-- =====================================================================
USE `mars_live`;

-- ---------------------------------------------------------------
-- 1. 用户封禁（API-ADM-02/03）
-- ---------------------------------------------------------------
ALTER TABLE `user`
  ADD COLUMN `ban_reason` VARCHAR(255) NULL COMMENT '封禁原因（解封置 NULL）' AFTER `status`,
  ADD COLUMN `banned_at`  DATETIME(3) NULL COMMENT '封禁时间' AFTER `ban_reason`;

-- ---------------------------------------------------------------
-- 2. 实名核验（API-AUTH-10/11 · OP-5）
--    既有 user_auth 缺「失败原因 / 尝试次数」，补两列即可承载状态机审计。
-- ---------------------------------------------------------------
ALTER TABLE `user_auth`
  ADD COLUMN `real_name`     VARCHAR(64)  NULL COMMENT '实名姓名（核验通过后回填，展示用）' AFTER `verify_channel`,
  ADD COLUMN `fail_reason`   VARCHAR(255) NULL COMMENT '核验失败原因（FAILED 时填充）' AFTER `real_name`,
  ADD COLUMN `attempt_count` INT NOT NULL DEFAULT 0 COMMENT '核验尝试次数（连续 5 次锁定）' AFTER `fail_reason`;

-- ---------------------------------------------------------------
-- 3. 类目受限位（API-ADM-17/18 · OP-7）
--    一期 4 类；`restricted` 表示是否限制未成年人观看（仅展示，服务端不强制）。
-- ---------------------------------------------------------------
ALTER TABLE `live_category`
  ADD COLUMN `restricted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否限制未成年人（展示位）' AFTER `require_qualification`;

-- ---------------------------------------------------------------
-- 4. 运营位 / 轮播（API-ADM-19 · FR-11.2）
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `banner` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `title`        VARCHAR(128) NOT NULL,
  `target_type`  VARCHAR(16)  NOT NULL COMMENT 'ROOM / CATEGORY / URL',
  `target_value` VARCHAR(512) NOT NULL DEFAULT '',
  `start_at`     DATETIME(3)  NULL,
  `end_at`       DATETIME(3)  NULL,
  `enabled`      TINYINT(1)   NOT NULL DEFAULT 1,
  `sort`         INT          NOT NULL DEFAULT 0,
  `created_at`   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_banner_enabled_sort` (`enabled`, `sort`)
) ENGINE=InnoDB COMMENT='运营位 / 首页轮播（FR-11.2）';

-- ---------------------------------------------------------------
-- 5. 发票（API-ADM-28 · OP-11 / OP-22）
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `invoice` (
  `invoice_id`  VARCHAR(64)  NOT NULL,
  `order_no`    VARCHAR(64)  NOT NULL,
  `buyer`       VARCHAR(128) NOT NULL DEFAULT '',
  `amount_fen`  BIGINT       NOT NULL,
  `channel`     VARCHAR(16)  NOT NULL COMMENT 'WECHAT / ALIPAY / IAP',
  `status`      VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / ISSUED',
  `invoice_url` VARCHAR(512) NULL,
  `created_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `issued_at`   DATETIME(3)  NULL,
  PRIMARY KEY (`invoice_id`),
  UNIQUE KEY `uk_invoice_order_no` (`order_no`),
  KEY `idx_invoice_status` (`status`)
) ENGINE=InnoDB COMMENT='发票（财务开具后回传 invoice_url；OP-22 按 invoice.iap_mode 分流）';

-- ---------------------------------------------------------------
-- 6. 提现打款字段（API-ADM-24/25/26 · OP-16）
--    V1 的 withdrawal 只承载申请与审核；补「银行卡（脱敏）/ 打款时点 / 审核备注」。
-- ---------------------------------------------------------------
ALTER TABLE `withdrawal`
  ADD COLUMN `tax_mode`            VARCHAR(16)  NOT NULL DEFAULT 'SETTLE' COMMENT '个税扣缴时点 SETTLE/WITHDRAW（OP-23）' AFTER `tax_fen`,
  ADD COLUMN `period`              VARCHAR(7)   NULL COMMENT '归属账期 YYYY-MM（财务列表按月筛选）' AFTER `tax_mode`,
  ADD COLUMN `bank_account_masked` VARCHAR(64)  NULL COMMENT '银行卡号脱敏（展示）' AFTER `reject_reason`,
  ADD COLUMN `bank_name_match`     TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '银行卡姓名与实名一致（BR-6）' AFTER `bank_account_masked`,
  ADD COLUMN `net_amount_fen`      BIGINT       NOT NULL DEFAULT 0 COMMENT '到账金额（WITHDRAW 模式扣税后）' AFTER `bank_name_match`,
  ADD COLUMN `paid_at`             DATETIME(3)  NULL COMMENT '打款完成时间' AFTER `net_amount_fen`;

-- ---------------------------------------------------------------
-- 7. 刷新令牌黑名单（API-AUTH-05/06 refresh 轮换 + logout 吊销）
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `refresh_token` (
  `token_hash` VARCHAR(64)  NOT NULL COMMENT 'SHA-256(refresh_token)',
  `user_id`    BIGINT       NOT NULL,
  `expires_at` DATETIME(3)  NOT NULL,
  `revoked`    TINYINT(1)   NOT NULL DEFAULT 0,
  `created_at` DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`token_hash`),
  KEY `idx_refresh_token_user` (`user_id`),
  KEY `idx_refresh_token_expiry` (`expires_at`)
) ENGINE=InnoDB COMMENT='刷新令牌（轮换 + 注销吊销；TLS 就绪后可迁 Redis）';
