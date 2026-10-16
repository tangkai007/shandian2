/** 后台接口调用配置（内部类型，非契约 DTO）。 */
export interface AdminRequestConfig {
  path: string;
  method?: 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE';
  params?: Record<string, unknown>;
  body?: unknown;
  /** 业务失败文案的占位符 —— 金额 / 阈值一律由调用方注入，**不在文案层硬编码**。 */
  messageParams?: Record<string, string>;
}
