import request from '@/utils/request';

export interface ImportResult {
  added: number;
  updated: number;
  skipped: number;
}

export const dataTransferApi = {
  /** 导出当前用户全部简历配置（与 /api/sync/full 相同结构） */
  export: () => request.get<any, Record<string, any>>('/data/export'),
  /** 导入 JSON：mode=merge 仅新增 / overwrite 覆盖同键内容 */
  import: (payload: Record<string, any>, mode: 'merge' | 'overwrite') =>
    request.post<any, ImportResult>('/data/import', { payload, mode }),
};
