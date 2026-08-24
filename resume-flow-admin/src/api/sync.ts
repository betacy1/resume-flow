import request from '@/utils/request';

export interface SyncStatusVO {
  profileVersion: number;
  dataHash: string;
  updatedAt: string;
}

export const syncApi = {
  /** 当前用户数据版本、内容哈希与最后更新时间 */
  status: () => request.get<any, SyncStatusVO>('/sync/status'),
};
