/**
 * 存储服务 - 封装 chrome.storage.local 操作
 */

const STORAGE_KEYS = {
  TOKEN: 'rf_token',
  USER_ID: 'rf_user_id',
  USERNAME: 'rf_username',
  BACKEND_URL: 'rf_backend_url',
  SELECTED_TEMPLATE_ID: 'rf_template_id',
  SELECTED_TEMPLATE_NAME: 'rf_template_name',
} as const;

export interface StoredAuth {
  token: string;
  userId: number;
  username: string;
  backendUrl: string;
}

/** 获取存储中的认证信息 */
export async function getAuth(): Promise<StoredAuth | null> {
  return new Promise((resolve) => {
    chrome.storage.local.get(
      [STORAGE_KEYS.TOKEN, STORAGE_KEYS.USER_ID, STORAGE_KEYS.USERNAME, STORAGE_KEYS.BACKEND_URL],
      (result) => {
        if (result[STORAGE_KEYS.TOKEN]) {
          resolve({
            token: result[STORAGE_KEYS.TOKEN],
            userId: result[STORAGE_KEYS.USER_ID],
            username: result[STORAGE_KEYS.USERNAME],
            backendUrl: result[STORAGE_KEYS.BACKEND_URL] || 'http://localhost:8080',
          });
        } else {
          resolve(null);
        }
      },
    );
  });
}

/** 保存认证信息 */
export async function saveAuth(data: StoredAuth): Promise<void> {
  return new Promise((resolve) => {
    chrome.storage.local.set(
      {
        [STORAGE_KEYS.TOKEN]: data.token,
        [STORAGE_KEYS.USER_ID]: data.userId,
        [STORAGE_KEYS.USERNAME]: data.username,
        [STORAGE_KEYS.BACKEND_URL]: data.backendUrl,
      },
      () => resolve(),
    );
  });
}

/** 清除认证信息 */
export async function clearAuth(): Promise<void> {
  return new Promise((resolve) => {
    chrome.storage.local.remove(
      [STORAGE_KEYS.TOKEN, STORAGE_KEYS.USER_ID, STORAGE_KEYS.USERNAME],
      () => resolve(),
    );
  });
}

/** 获取选中的模板 */
export async function getSelectedTemplate(): Promise<{ id: number; name: string } | null> {
  return new Promise((resolve) => {
    chrome.storage.local.get(
      [STORAGE_KEYS.SELECTED_TEMPLATE_ID, STORAGE_KEYS.SELECTED_TEMPLATE_NAME],
      (result) => {
        if (result[STORAGE_KEYS.SELECTED_TEMPLATE_ID]) {
          resolve({
            id: result[STORAGE_KEYS.SELECTED_TEMPLATE_ID],
            name: result[STORAGE_KEYS.SELECTED_TEMPLATE_NAME] || '',
          });
        } else {
          resolve(null);
        }
      },
    );
  });
}

/** 保存选中的模板 */
export async function saveSelectedTemplate(id: number, name: string): Promise<void> {
  return new Promise((resolve) => {
    chrome.storage.local.set(
      {
        [STORAGE_KEYS.SELECTED_TEMPLATE_ID]: id,
        [STORAGE_KEYS.SELECTED_TEMPLATE_NAME]: name,
      },
      () => resolve(),
    );
  });
}

/** 获取后端地址 */
export async function getBackendUrl(): Promise<string> {
  return new Promise((resolve) => {
    chrome.storage.local.get([STORAGE_KEYS.BACKEND_URL], (result) => {
      resolve(result[STORAGE_KEYS.BACKEND_URL] || 'http://localhost:8080');
    });
  });
}
