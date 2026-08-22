import { login as apiLogin, getMe } from './apiClient';
import { saveAuth, clearAuth, getAuth, StoredAuth } from './storageService';

/**
 * 认证服务 - 处理登录/登出逻辑
 */

/** 登录并保存 token */
export async function login(
  username: string,
  password: string,
  backendUrl: string,
): Promise<{ userId: number; username: string }> {
  const result = await apiLogin(username, password, backendUrl);

  const authData: StoredAuth = {
    token: result.token,
    userId: result.userId,
    username: result.username,
    backendUrl,
  };

  await saveAuth(authData);
  return { userId: result.userId, username: result.username };
}

/** 登出 */
export async function logout(): Promise<void> {
  await clearAuth();
}

/** 检查是否已登录 */
export async function isLoggedIn(): Promise<boolean> {
  const auth = await getAuth();
  return auth !== null && !!auth.token;
}

/** 获取当前认证信息 */
export async function getStoredAuth(): Promise<StoredAuth | null> {
  return getAuth();
}
