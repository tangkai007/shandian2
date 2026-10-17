-- =====================================================================
-- MARS-1 · 增量 4 迁移：审核域（AC-7）/ 回放（AC-9）/ 后台审计（API-ADM-29）
--
-- 依据：S1a v0.4 §6.2（表结构）+ S2a v1.2 §4.9/§4.10（后台与回调语义）。
-- 约定：审核 / 举报 / 申诉 / 处置记录**不可物理删除**（AC-7）—— 只允许状态流转，
--       故除 `webhook_event`（纯技术幂等表）外一律不带 DELETE 路径。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 审核任务（机审回调分流队列，API-CB-03/04）
--    - event_id 唯一：回调幂等（同一事件重复投递只入队一次，§4.10）
--    - risk_level：HIGH/MEDIUM/LOW，队列按风险分排序（API-ADM-06）
--    - anchor_name：审计/留痕标签「主播（房号 N）」需要昵称（入队时由 live_room+user 回填）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `moderation_task` (
  `task_id`        VARCHAR(64)  NOT NULL,
  `event_id`       VARCHAR(128) NOT NULL COMMENT '回调事件 ID（幂等键，§4.10）',
  `source`         VARCHAR(16)  NOT NULL COMMENT 'CSS_IMAGE / CSS_CMS',
  `room_id`        BIGINT       NULL,
  `room_no`        BIGINT       NULL,
  `anchor_id`      BIGINT       NULL,
  `anchor_name`    VARCHAR(64)  NULL COMMENT '主播昵称（审计标签用）',
  `session_id`     BIGINT       NULL,
  `risk_level`     VARCHAR(8)   NOT NULL DEFAULT 'LOW' COMMENT 'HIGH/MEDIUM/LOW',
  `alert_type`     VARCHAR(8)   NOT NULL DEFAULT 'IMAGE' COMMENT 'IMAGE/TEXT/AUDIO',
  `reason_summary` VARCHAR(255) NOT NULL DEFAULT '',
  `snapshot_url`   VARCHAR(512) NOT NULL DEFAULT '',
  `rule_id`        VARCHAR(64)  NOT NULL DEFAULT '',
  `confidence`     INT          NOT NULL DEFAULT 0 COMMENT '置信度 0–100',
  `status`         VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/CLAIMED/DONE',
  `claimed_by`     BIGINT       NULL,
  `claimed_by_name` VARCHAR(64) NULL,
  `created_at`     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`task_id`),
  UNIQUE KEY `uk_moderation_task_event` (`event_id`),
  KEY `idx_moderation_task_queue` (`status`, `risk_level`, `created_at`)
) ENGINE=InnoDB COMMENT='审核任务（机审回调风险分流队列，AC-7）';

-- ---------------------------------------------------------------------
-- 2. 处置记录（API-ADM-08）—— **不可物理删除**（AC-7）
--    - appeal_deadline_at = action_time + 申诉时限（取 sys_config.appeal_days，非硬编码）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `moderation_action` (
  `action_id`          VARCHAR(64)  NOT NULL,
  `task_id`            VARCHAR(64)  NULL,
  `room_id`            BIGINT       NULL,
  `session_id`         BIGINT       NULL,
  `target_user_id`     BIGINT       NULL COMMENT '被处置对象（封禁时为主播账号）',
  `action_type`        VARCHAR(16)  NOT NULL COMMENT 'WARN/CUT/BAN/REPORT',
  `reason_code`        VARCHAR(64)  NOT NULL,
  `remark`             VARCHAR(512) NOT NULL,
  `operator_id`        BIGINT       NOT NULL,
  `operator_name`      VARCHAR(64)  NOT NULL DEFAULT '',
  `notify_channels`    VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '逗号分隔：INBOX/PUSH/SMS',
  `evidence_id`        VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '证据快照 ID（与 moderation_evidence 一对一）',
  `action_time`        DATETIME(3)  NOT NULL,
  `appeal_deadline_at` DATETIME(3)  NOT NULL COMMENT '= action_time + 申诉时限（后台配置）',
  `disposition_effective` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '申诉期间处置不解除（BR-8）',
  `created_at`         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`action_id`),
  KEY `idx_moderation_action_task` (`task_id`),
  KEY `idx_moderation_action_room` (`room_id`, `action_time`)
) ENGINE=InnoDB COMMENT='处置记录（不可物理删除，AC-7）';

-- ---------------------------------------------------------------------
-- 3. 证据快照（API-ADM-09）—— 与处置一一对应，不可删
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `moderation_evidence` (
  `evidence_id`    VARCHAR(64)  NOT NULL,
  `action_id`      VARCHAR(64)  NOT NULL,
  `snapshot_at`    DATETIME(3)  NULL,
  `rule_id`        VARCHAR(64)  NOT NULL DEFAULT '',
  `confidence`     INT          NOT NULL DEFAULT 0,
  `operator_name`  VARCHAR(64)  NOT NULL DEFAULT '',
  `evidence_urls`  VARCHAR(2048) NOT NULL DEFAULT '' COMMENT 'JSON 数组（COS 地址）',
  `created_at`     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`evidence_id`),
  UNIQUE KEY `uk_moderation_evidence_action` (`action_id`)
) ENGINE=InnoDB COMMENT='处置证据快照（不可物理删除，AC-7）';

-- ---------------------------------------------------------------------
-- 4. 用户举报（API-INT-09 → 审核队列；API-ADM-10/11 处理）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `content_report` (
  `report_id`       VARCHAR(64)  NOT NULL,
  `reporter_id`     BIGINT       NOT NULL,
  `target_type`     VARCHAR(16)  NOT NULL COMMENT 'ROOM/USER/DANMAKU',
  `target_id`       VARCHAR(64)  NOT NULL,
  `target_label`    VARCHAR(128) NOT NULL DEFAULT '',
  `room_id`         BIGINT       NULL,
  `reason_type`     VARCHAR(64)  NOT NULL,
  `reason_desc`     VARCHAR(512) NOT NULL DEFAULT '',
  `images`          VARCHAR(2048) NOT NULL DEFAULT '' COMMENT 'JSON 数组',
  `status`          VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/HANDLED/REJECTED',
  `handled_by`      BIGINT       NULL,
  `handle_reason_code` VARCHAR(64) NULL,
  `handle_remark`   VARCHAR(512) NULL,
  `created_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `handled_at`      DATETIME(3)  NULL,
  PRIMARY KEY (`report_id`),
  KEY `idx_content_report_queue` (`status`, `created_at`),
  KEY `idx_content_report_target` (`target_type`, `target_id`)
) ENGINE=InnoDB COMMENT='用户举报（进审核队列，FR-9.4）';

-- ---------------------------------------------------------------------
-- 5. 申诉（BR-8 / OP-17；API-ADM-12/13）—— 不可物理删除
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `appeal` (
  `appeal_id`      VARCHAR(64)  NOT NULL,
  `action_id`      VARCHAR(64)  NOT NULL,
  `appellant_id`   BIGINT       NOT NULL,
  `action_type`    VARCHAR(16)  NOT NULL,
  `reason_code`    VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '原处置原因码',
  `content`        VARCHAR(1024) NOT NULL DEFAULT '' COMMENT '申诉说明',
  `action_time`    DATETIME(3)  NOT NULL,
  `deadline_at`    DATETIME(3)  NOT NULL COMMENT '= action_time + sys_config.appeal_days',
  `status`         VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ACCEPTED/REJECTED',
  `handled_by`     BIGINT       NULL,
  `handle_remark`  VARCHAR(512) NULL,
  `created_at`     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `handled_at`     DATETIME(3)  NULL,
  PRIMARY KEY (`appeal_id`),
  UNIQUE KEY `uk_appeal_action` (`action_id`) COMMENT '一条处置仅可申诉一次',
  KEY `idx_appeal_queue` (`status`, `created_at`)
) ENGINE=InnoDB COMMENT='处置申诉（BR-8；申诉期间处置不解除）';

-- ---------------------------------------------------------------------
-- 6. 后台操作审计（API-ADM-29，保留 ≥1 年）
--    契约 §4.9 措辞为「AOP 切面」；本实现以显式 AuditRecorder 落库（见实现说明的偏差登记）。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `admin_operation_log` (
  `log_id`        VARCHAR(64)  NOT NULL,
  `operator_id`   BIGINT       NOT NULL,
  `operator_name` VARCHAR(64)  NOT NULL DEFAULT '',
  `operator_role` VARCHAR(32)  NOT NULL DEFAULT '',
  `action`        VARCHAR(64)  NOT NULL COMMENT 'WARN/CUT/BAN/REPORT/MARK_OK/FORCE_STOP/REPORT_HANDLE/APPEAL_ACCEPT/APPEAL_REJECT/SYS_CONFIG_UPDATE',
  `target_type`   VARCHAR(32)  NOT NULL DEFAULT '',
  `target_id`     VARCHAR(64)  NOT NULL DEFAULT '',
  `target_label`  VARCHAR(128) NOT NULL DEFAULT '',
  `reason_code`   VARCHAR(64)  NULL,
  `remark`        VARCHAR(512) NULL,
  `evidence_id`   VARCHAR(64)  NULL,
  `before_json`   JSON         NULL,
  `after_json`    JSON         NULL,
  `ip`            VARCHAR(64)  NOT NULL DEFAULT '',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`log_id`),
  KEY `idx_admin_operation_log_time` (`created_at`),
  KEY `idx_admin_operation_log_operator` (`operator_id`, `created_at`)
) ENGINE=InnoDB COMMENT='后台操作审计（API-ADM-29，保留 ≥1 年）';

-- ---------------------------------------------------------------------
-- 7. 回放（AC-9；API-CB-02 录制回调 → API-REP-01/02）
--    expire_at = ended_at + replay_retention_days（后台配置，默认 30）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `replay_record` (
  `replay_id`    VARCHAR(64)  NOT NULL,
  `session_id`   BIGINT       NULL,
  `room_id`      BIGINT       NOT NULL,
  `anchor_id`    BIGINT       NULL,
  `vod_file_id`  VARCHAR(128) NOT NULL DEFAULT '',
  `play_url`     VARCHAR(1024) NOT NULL DEFAULT '',
  `duration_sec` INT          NOT NULL DEFAULT 0,
  `started_at`   DATETIME(3)  NULL,
  `ended_at`     DATETIME(3)  NULL,
  `expire_at`    DATETIME(3)  NOT NULL COMMENT '= ended_at + 回放留存天数（后台配置）',
  `status`       VARCHAR(16)  NOT NULL DEFAULT 'READY' COMMENT 'READY/EXPIRED/REMOVED',
  `created_at`   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`replay_id`),
  UNIQUE KEY `uk_replay_record_vod` (`vod_file_id`),
  KEY `idx_replay_record_room` (`room_id`, `started_at`)
) ENGINE=InnoDB COMMENT='回放记录（AC-9）';

-- ---------------------------------------------------------------------
-- 8. 回调事件幂等表（§4.10：事件 ID 去重）—— 纯技术表，允许清理旧行
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `webhook_event` (
  `event_id`    VARCHAR(128) NOT NULL,
  `source`      VARCHAR(16)  NOT NULL,
  `received_at` DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`event_id`)
) ENGINE=InnoDB COMMENT='第三方回调事件去重（§4.10）';
