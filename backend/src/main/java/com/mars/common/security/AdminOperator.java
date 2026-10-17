package com.mars.common.security;

/**
 * 后台操作者身份快照（身份取自服务端鉴权上下文，不信任前端传参 —— AC-10）。
 *
 * @param id   操作者用户 ID
 * @param name 操作者展示名（写审计前由 {@code AuditRecorder} 用昵称覆盖，见「已知限制 9」闭合）
 * @param role 审计落库用的小写角色（{@code auditor/ops/finance}）
 * @param ip   来源 IP
 */
public record AdminOperator(long id, String name, String role, String ip) {
}
