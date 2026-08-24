/**
 * 同步缓存服务
 * 保存服务端下发的全量简历数据与本地版本信息，
 * 网络不可用时插件使用缓存继续填写（界面提示"可能不是最新版本"）。
 */

import type { SyncFullPayload } from './apiClient';

const CACHE_KEY = 'rf_sync_cache';

export interface SyncCache {
  currentTemplateId: number | null;
  currentProfileVersion: number;
  currentDataHash: string;
  lastSyncTime: string | null;
  cachedProfileData: Record<string, any> | null;
  cachedFields: any[];
  cachedMaterials: any[];
  cachedTemplates: any[];
  cachedSkillProfiles: any[];
  cachedTemplateConfigs: any[];
  cachedContentVariants: any[];
  cachedInternships: any[];
  cachedProjects: any[];
  cachedEducation: any[];
}

export async function getSyncCache(): Promise<SyncCache | null> {
  return new Promise((resolve) => {
    chrome.storage.local.get([CACHE_KEY], (result) => {
      resolve(result[CACHE_KEY] || null);
    });
  });
}

/** 用全量同步载荷覆盖本地缓存 */
export async function saveSyncCache(payload: SyncFullPayload): Promise<SyncCache> {
  const cache: SyncCache = {
    currentTemplateId: null,
    currentProfileVersion: payload.profileVersion,
    currentDataHash: payload.dataHash,
    lastSyncTime: new Date().toISOString(),
    cachedProfileData: payload.basicInfo,
    cachedFields: payload.customFields || [],
    cachedMaterials: payload.materials || [],
    cachedTemplates: payload.templates || [],
    cachedSkillProfiles: payload.skillList || [],
    cachedTemplateConfigs: payload.templateConfigs || [],
    cachedContentVariants: payload.contentVariants || [],
    cachedInternships: payload.internshipList || [],
    cachedProjects: payload.projectList || [],
    cachedEducation: payload.educationList || [],
  };
  // 保留既有模板选择
  const old = await getSyncCache();
  if (old?.currentTemplateId) {
    cache.currentTemplateId = old.currentTemplateId;
  }
  return new Promise((resolve) => {
    chrome.storage.local.set({ [CACHE_KEY]: cache }, () => resolve(cache));
  });
}

/** 更新缓存中的部分字段（如本地模板选择） */
export async function patchSyncCache(patch: Partial<SyncCache>): Promise<void> {
  return new Promise((resolve) => {
    chrome.storage.local.get([CACHE_KEY], (result) => {
      const cache = result[CACHE_KEY] || null;
      if (!cache) {
        resolve();
        return;
      }
      chrome.storage.local.set({ [CACHE_KEY]: { ...cache, ...patch } }, () => resolve());
    });
  });
}

/** 用导入的缓存对象整体覆盖本地缓存 */
export async function restoreSyncCache(imported: SyncCache): Promise<void> {
  return new Promise((resolve) => {
    chrome.storage.local.set({ [CACHE_KEY]: imported }, () => resolve());
  });
}

/** 清除缓存（退出登录时） */
export async function clearSyncCache(): Promise<void> {
  return new Promise((resolve) => {
    chrome.storage.local.remove([CACHE_KEY], () => resolve());
  });
}
