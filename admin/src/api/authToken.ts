const STORAGE_KEY = 'mars1.admin.access_token';

let inMemoryToken: string | null = null;

/** 后台会话令牌（一期为内存 + sessionStorage；正式接入走 HttpOnly Cookie 或安全存储）。 */
export function getAccessToken(): string | null {
  if (inMemoryToken !== null) {
    return inMemoryToken;
  }
  try {
    inMemoryToken = globalThis.sessionStorage?.getItem(STORAGE_KEY) ?? null;
  } catch {
    inMemoryToken = null;
  }
  return inMemoryToken;
}

export function setAccessToken(token: string): void {
  inMemoryToken = token;
  try {
    globalThis.sessionStorage?.setItem(STORAGE_KEY, token);
  } catch {
    /* 存储不可用时仅保留内存态 */
  }
}

export function clearAccessToken(): void {
  inMemoryToken = null;
  try {
    globalThis.sessionStorage?.removeItem(STORAGE_KEY);
  } catch {
    /* 同上 */
  }
}
