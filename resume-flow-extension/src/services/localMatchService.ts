/**
 * 本地推荐匹配服务
 * 用户点击招聘网站输入框后，根据当前模板、岗位方向、字段文本与字数限制，
 * 在本地缓存的自定义字段中推荐最匹配的 3 条内容（不发请求，离线可用）。
 */

import type { CustomFieldItem, FieldInfo } from './apiClient';

export interface RecommendItem {
  field: CustomFieldItem;
  score: number;
  /** 推荐填入的正文（已按字数限制截断标记） */
  content: string;
  /** 输入框字数限制 */
  limit: number | null;
  /** 是否超出字数限制 */
  overLimit: boolean;
}

/** 提取输入框的全部可比较文本（小写化） */
function fieldText(info: FieldInfo): string {
  return [
    info.label, info.questionText, info.placeholder, info.name, info.id,
    info.ariaLabel, info.nearbyText, info.parentText,
  ].filter(Boolean).join(' ').toLowerCase();
}

/** 字段 key 切分为可比较的词元：arrival_date → [arrival, date] */
function keyTokens(fieldKey: string): string[] {
  return (fieldKey || '').toLowerCase().split(/[_\-.]/).filter((t) => t.length >= 2);
}

/**
 * 在缓存字段中推荐最匹配的 3 条：
 * 匹配关键词命中权重最高，其次字段名与输入框文本互相包含，再次 fieldKey 词元命中；
 * 过滤已禁用与不参与填充的字段；模板范围不符（templateIds 非空且不含当前模板）降权但不剔除。
 */
export function recommendFields(
  info: FieldInfo,
  fields: CustomFieldItem[],
  opts: { templateId?: number | null; limit?: number | null } = {},
): RecommendItem[] {
  const text = fieldText(info);
  if (!text.trim()) return [];
  const limit = opts.limit ?? info.wordLimit ?? null;
  const items: RecommendItem[] = [];

  for (const f of fields) {
    if (f.enabled === false || f.autoFillEnabled === false) continue;
    if (!f.fieldValue) continue;
    let score = 0;

    for (const kw of f.matchKeywords || []) {
      const k = kw.trim().toLowerCase();
      if (k && text.includes(k)) score += 3;
    }
    const name = (f.fieldName || '').toLowerCase();
    if (name && (text.includes(name) || name.includes(text.slice(0, 20)))) score += 2;
    for (const token of keyTokens(f.fieldKey)) {
      if (text.includes(token)) score += 1;
    }
    if (opts.templateId && (f.templateIds || []).length > 0 && !f.templateIds!.includes(opts.templateId)) {
      score -= 2;
    }

    if (score <= 0) continue;
    const content = String(f.fieldValue);
    items.push({
      field: f,
      score,
      content,
      limit,
      overLimit: limit != null && content.length > limit,
    });
  }

  items.sort((a, b) => b.score - a.score);
  return items.slice(0, 3);
}
