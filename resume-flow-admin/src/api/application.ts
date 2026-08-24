import request from '@/utils/request';
import { useAuthStore } from '@/stores/auth';

/** 投递记录（与后端 ApplicationRecordDTO 对应） */
export interface ApplicationRecord {
  id?: number;
  batchName?: string;
  sourceType?: string;
  categoryType?: string;
  companyName?: string;
  organizationName?: string;
  positionName?: string;
  positionDirection?: string;
  companyNature?: string;
  applyStatus?: string;
  currentStage?: string;
  priority?: string;
  city?: string;
  applicationChannel?: string;
  officialWebsite?: string;
  publicAccount?: string;
  recruitmentUrl?: string;
  applicationUrl?: string;
  resumeEditUrl?: string;
  pageUrl?: string;
  pageTitle?: string;
  domain?: string;
  resumeModifiedAt?: string;
  resumeModifiedSource?: string;
  resumeModifiedRemark?: string;
  firstDetectedAt?: string;
  lastVisitedAt?: string;
  appliedAt?: string;
  deadlineAt?: string;
  remark?: string;
  warningNote?: string;
  confidenceScore?: number;
  nameManuallyEdited?: boolean;
  sortOrder?: number;
  enabled?: boolean;
  createTime?: string;
  updateTime?: string;
}

export interface ApplicationStageRecord {
  id?: number;
  applicationRecordId?: number;
  stageName?: string;
  stageStatus?: string;
  stageResult?: string;
  stageTime?: string;
  note?: string;
  sortOrder?: number;
}

export interface ApplicationQuery {
  page?: number;
  size?: number;
  batchName?: string;
  applyStatus?: string;
  sourceType?: string;
  companyName?: string;
  organizationName?: string;
  positionName?: string;
  companyNature?: string;
  applicationChannel?: string;
  currentStage?: string;
  pluginCollected?: boolean;
  keyword?: string;
  sortBy?: string;
  sortDir?: string;
}

export interface ApplicationOptions {
  applyStatuses: string[];
  sourceTypes: string[];
  channels: string[];
  currentStages: string[];
  priorities: string[];
  batchNames: string[];
  companyNatures: string[];
  categoryTypes: string[];
  stageNames: string[];
  stageStatuses: string[];
  stageResults: string[];
}

export interface ApplicationPage {
  total: number;
  page: number;
  size: number;
  records: ApplicationRecord[];
}

export const applicationApi = {
  list(query: ApplicationQuery): Promise<ApplicationPage> {
    return request.get('/application-records', { params: query });
  },
  options(): Promise<ApplicationOptions> {
    return request.get('/application-records/options');
  },
  create(data: ApplicationRecord): Promise<ApplicationRecord> {
    return request.post('/application-records', data);
  },
  update(id: number, data: ApplicationRecord): Promise<ApplicationRecord> {
    return request.put(`/application-records/${id}`, data);
  },
  updateStatus(id: number, applyStatus: string): Promise<ApplicationRecord> {
    return request.put(`/application-records/${id}/status`, null, { params: { applyStatus } });
  },
  batchStatus(ids: number[], applyStatus: string): Promise<number> {
    return request.post('/application-records/batch-status', { ids, applyStatus });
  },
  remove(id: number): Promise<void> {
    return request.delete(`/application-records/${id}`);
  },
  copy(id: number): Promise<ApplicationRecord> {
    return request.post(`/application-records/${id}/copy`);
  },
  listStages(recordId: number): Promise<ApplicationStageRecord[]> {
    return request.get(`/application-records/${recordId}/stages`);
  },
  createStage(recordId: number, data: ApplicationStageRecord): Promise<ApplicationStageRecord> {
    return request.post(`/application-records/${recordId}/stages`, data);
  },
  updateStage(stageId: number, data: ApplicationStageRecord): Promise<ApplicationStageRecord> {
    return request.put(`/application-stages/${stageId}`, data);
  },
  deleteStage(stageId: number): Promise<void> {
    return request.delete(`/application-stages/${stageId}`);
  },
  importExcel(file: File): Promise<{ created: number; updated: number; skipped: number }> {
    const form = new FormData();
    form.append('file', file);
    return request.post('/application-records/import', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
  /** 导出 Excel（blob 下载，文件名取后端 Content-Disposition） */
  async exportExcel(): Promise<void> {
    const authStore = useAuthStore();
    const response = await fetch('/api/application-records/export', {
      headers: { Authorization: `Bearer ${authStore.token}` },
    });
    if (!response.ok) {
      throw new Error(`导出失败：HTTP ${response.status}`);
    }
    const disposition = response.headers.get('Content-Disposition') || '';
    let fileName = 'ResumeFlow_投递信息表.xlsx';
    const utf8Match = /filename\*=UTF-8''([^;]+)/.exec(disposition);
    if (utf8Match) {
      fileName = decodeURIComponent(utf8Match[1]);
    }
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName;
    link.click();
    URL.revokeObjectURL(url);
  },
};
