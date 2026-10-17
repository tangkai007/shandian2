-- =====================================================================
-- V5 · IAP 交易金额列补齐（B-3 修复 · 契约 §4.6 API-ASSET-07/08 · OP-11 · RISK-7）
--
-- 背景：V1__init.sql 的 `iap_transaction` 只有 id / transaction_id / user_id /
--       product_id / channel_fee_rate / env / status / created_at；而领域模型
--       `com.mars.asset.iap.IapTransaction` 的 amountFen / coinAmount 与
--       `IapVerifyService` 的落库(:80) 及退款回读(:104/:113/:114) 都依赖这两列，
--       缺失会导致「加币落库无列可依」。
-- 说明：只补列，不改 V1…V4 既有列语义；`NOT NULL DEFAULT 0` 保证老行安全
--       （历史 IAP 行本就不存在，默认值仅为升级路径兜底）。
-- 列序对齐模型字段序：transaction_id, user_id, product_id, amount_fen,
--       coin_amount, channel_fee_rate, env, status。
-- =====================================================================
USE `mars_live`;

-- ---------------------------------------------------------------
-- 1. 补两列（下单快照，不随退款/撤销变更）
-- ---------------------------------------------------------------
ALTER TABLE `iap_transaction`
  ADD COLUMN `amount_fen`  BIGINT NOT NULL DEFAULT 0 COMMENT '记账金额（分，下单快照）' AFTER `product_id`,
  ADD COLUMN `coin_amount` BIGINT NOT NULL DEFAULT 0 COMMENT '到账币数（下单快照）' AFTER `amount_fen`;
