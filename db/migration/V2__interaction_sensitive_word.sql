-- MARS-1 · V2：互动域合规词库（AC-4 前置）
-- 事实来源：S1a v0.4 §3.5（P4.24 发送前敏感词校验与限流）+ S2a v1.2 §4.5
--
-- 背景：V1 只有内置资源词表，运营无法改词库。本迁移提供可运营配置的词表，
--       与内置资源词表在 CompositeSensitiveWordSource 中合并后进前缀树，按 TTL 重建。

USE `mars_live`;

CREATE TABLE IF NOT EXISTS `sensitive_word` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `word`        VARCHAR(64) NOT NULL COMMENT '敏感词（归一化后入树：小写 + 剔除空白/标点）',
  `category`    VARCHAR(32) NOT NULL DEFAULT 'DEFAULT' COMMENT '分类（便于分场景启用/统计）',
  `status`      VARCHAR(16) NOT NULL DEFAULT 'ON' COMMENT 'ON / OFF',
  `operator_id` BIGINT      NULL COMMENT '最后操作的后台账号',
  `created_at`  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sensitive_word` (`word`),
  KEY `idx_sensitive_status` (`status`)
) ENGINE=InnoDB COMMENT='运营可配置敏感词（与内置资源词库合并使用，改词库不发版）';

-- 种子：仅少量占位词，与 src/main/resources/sensitive-words.txt 的基线保持一致。
-- 线上合规词库由运营导入（或由供应商词库经 SensitiveWordSource 接入）。
INSERT INTO `sensitive_word` (`word`, `category`) VALUES
  ('违禁品', 'TRADE'),
  ('代充', 'TRADE'),
  ('代练', 'TRADE'),
  ('刷单', 'TRADE'),
  ('外挂', 'CHEAT'),
  ('私服', 'CHEAT'),
  ('赌博', 'GAMBLE'),
  ('博彩', 'GAMBLE'),
  ('私聊加微信', 'OFFSITE'),
  ('加微信转账', 'OFFSITE'),
  ('站外交易', 'OFFSITE')
ON DUPLICATE KEY UPDATE `category` = VALUES(`category`);
