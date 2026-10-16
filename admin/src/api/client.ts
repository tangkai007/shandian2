import axios from 'axios';
import { ErrorCodes, type ApiEnvelope } from '@/types/contract';
import { messageFor } from '@/domain/messages';
import { clearAccessToken, getAccessToken } from './authToken';
import { mockDispatch } from './mock';
import type { AdminRequestConfig } from './types';

/**
 * 统一请求层（契约 §2.2 统一响应 + §2.3 鉴权）。
 *
 * mock 与真实网关返回**同一形态**的响应封装，因此 `ApiError` 的构造、
 * `40100` 的清会话、文案取值只有一份实现 —— 切换后端不改变调用方行为。
 */

export class ApiError extends Error {
  readonly code: number;
  readonly traceId: string | undefined;
  readonly field: string | undefined;
  readonly params: Record<string, string>;

  constructor(code: number, message: string, options: {
    traceId?: string;
    field?: string;
    params?: Record<string, string>;
  } = {}) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.traceId = options.traceId;
    this.field = options.field;
    this.params = options.params ?? {};
  }

  /** 面向用户文案（S1b §09 / §8.2 三段式）。 */
  get userMessage(): string {
    return messageFor(this.code, {
      ...(this.field === undefined ? {} : { field: this.field }),
      params: this.params,
    });
  }
}

/** `VITE_USE_MOCK=false` 时切真实网关。 */
export const USE_MOCK = (import.meta.env.VITE_USE_MOCK ?? 'true') !== 'false';

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE ?? '/api/v1',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
});

http.interceptors.request.use((config) => {
  const token = getAccessToken();
  if (token !== null) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

async function httpDispatch(config: AdminRequestConfig): Promise<ApiEnvelope<unknown>> {
  const response = await http.request<ApiEnvelope<unknown>>({
    url: config.path,
    method: config.method ?? 'GET',
    ...(config.params === undefined ? {} : { params: config.params }),
    ...(config.body === undefined ? {} : { data: config.body }),
  });
  return response.data;
}

export async function request<T>(config: AdminRequestConfig): Promise<T> {
  const envelope = USE_MOCK
    ? await mockDispatch(config, getAccessToken())
    : await httpDispatch(config);

  if (envelope.code === ErrorCodes.ok) {
    return envelope.data as T;
  }

  if (envelope.code === ErrorCodes.unauthenticated) {
    // D-ADM-000：会话失效 → 清会话并重新登录（由路由守卫接管跳转）。
    clearAccessToken();
  }

  const data = (envelope.data ?? {}) as Record<string, unknown>;
  const field = typeof data.field === 'string' ? data.field : undefined;

  throw new ApiError(envelope.code, envelope.message, {
    ...(envelope.traceId === undefined ? {} : { traceId: envelope.traceId }),
    ...(field === undefined ? {} : { field }),
    ...(config.messageParams === undefined ? {} : { params: config.messageParams }),
  });
}
