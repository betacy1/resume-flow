<template>
  <div>
    <h2>专业技能管理</h2>

    <el-alert type="info" :closable="false" style="margin-bottom: 16px">
      七个技能分组为公共字段；保存后将按各模板的技能排序与侧重点，自动重新生成
      100 / 200 / 300 / 500 字与完整版本（字符数严格校验，中英文数字空格标点均计入）。
    </el-alert>

    <!-- 七个技能分组编辑 -->
    <el-card v-loading="loading" style="margin-bottom: 16px">
      <template #header><b>技能分组（公共内容）</b></template>
      <el-form label-width="140px">
        <el-form-item v-for="key in skillKeyOrder" :key="key" :label="bundle.skillKeys[key]">
          <el-input
            v-model="skillContent[key]"
            type="textarea"
            :rows="2"
            :placeholder="`请填写「${bundle.skillKeys[key]}」的技能描述`"
          />
          <span class="char-count">{{ (skillContent[key] || '').length }} 字</span>
        </el-form-item>
        <el-form-item label="技能关键词（公共）">
          <el-input v-model="keywords" type="textarea" :rows="2" placeholder="如 Java / Go / Spring Boot / Redis，保存后同步更新到全部模板" />
        </el-form-item>
      </el-form>
      <el-button type="primary" :loading="saving" @click="handleSave">保存并重新生成各模板版本</el-button>
      <el-button :loading="saving" @click="handleRegenerate">仅重新生成字数版本</el-button>
    </el-card>

    <!-- 各模板技能版本预览 -->
    <el-card v-loading="loading">
      <template #header><b>各模板专业技能版本预览</b></template>
      <div style="margin-bottom: 12px; display: flex; gap: 12px; align-items: center">
        <span>选择模板：</span>
        <el-select v-model="previewTemplateId" style="width: 220px" @change="() => {}">
          <el-option v-for="t in templates" :key="t.id" :label="t.name" :value="t.id!" />
        </el-select>
        <el-radio-group v-model="previewFieldType">
          <el-radio-button value="skill_short">简短版</el-radio-button>
          <el-radio-button value="skill_full">完整版</el-radio-button>
          <el-radio-button value="skill_keywords">关键词</el-radio-button>
        </el-radio-group>
      </div>

      <div v-if="previewTemplate">
        <p class="preview-meta">
          技能排序：<el-tag v-for="key in templateSkillOrder" :key="key" size="small" style="margin-right: 4px">
            {{ bundle.skillKeys[key] || key }}
          </el-tag>
        </p>
        <el-table :data="previewVariants" border>
          <el-table-column prop="lengthType" label="字数档位" width="120">
            <template #default="{ row }">{{ lengthLabel(row.lengthType) }}</template>
          </el-table-column>
          <el-table-column label="内容">
            <template #default="{ row }">
              <div style="white-space: pre-wrap">{{ row.content }}</div>
            </template>
          </el-table-column>
          <el-table-column label="实际字数" width="100">
            <template #default="{ row }">
              <el-tag :type="withinLimit(row) ? 'success' : 'danger'" size="small">
                {{ row.content.length }} 字
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { skillApi, type SkillBundle } from '@/api/skill';
import { templateApi, type ApplicationTemplateDTO } from '@/api/template';

const bundle = ref<SkillBundle>({ skillKeys: {}, skills: [], variants: [] });
const templates = ref<ApplicationTemplateDTO[]>([]);
const skillContent = reactive<Record<string, string>>({});
const keywords = ref('');
const loading = ref(false);
const saving = ref(false);
const previewTemplateId = ref<number | null>(null);
const previewFieldType = ref('skill_short');

/** 七个分组按后端返回顺序展示 */
const skillKeyOrder = computed(() => Object.keys(bundle.value.skillKeys));

const previewTemplate = computed(() =>
  templates.value.find((t) => t.id === previewTemplateId.value) || null);

/** 模板受众归一化：general_backend → general */
const previewAudience = computed(() => {
  const audience = previewTemplate.value?.audienceType || 'general';
  return audience === 'general_backend' ? 'general' : audience;
});

const templateSkillOrder = computed(() => {
  const order = (previewTemplate.value?.skillOrder || '').split(',').filter(Boolean);
  for (const key of skillKeyOrder.value) {
    if (!order.includes(key)) order.push(key);
  }
  return order;
});

const previewVariants = computed(() =>
  bundle.value.variants
    .filter((v) => v.audienceType === previewAudience.value && v.fieldType === previewFieldType.value)
    .sort((a, b) => lengthRank(b.lengthType) - lengthRank(a.lengthType)));

const LENGTH_RANK: Record<string, number> = {
  within_100: 0, within_200: 1, within_300: 2, within_500: 3, within_1000: 4, full: 5,
};
const LENGTH_LIMIT: Record<string, number> = {
  within_100: 100, within_200: 200, within_300: 300, within_500: 500, within_1000: 1000,
};

function lengthRank(lengthType: string): number {
  return LENGTH_RANK[lengthType] ?? -1;
}

function lengthLabel(lengthType: string): string {
  return lengthType === 'full' ? '完整版' : `${lengthType.replace('within_', '')} 字以内`;
}

function withinLimit(row: { lengthType: string; content: string }): boolean {
  const limit = LENGTH_LIMIT[row.lengthType];
  return limit == null || row.content.length <= limit;
}

async function loadData() {
  loading.value = true;
  try {
    const [skillBundle, templateList] = await Promise.all([skillApi.bundle(), templateApi.list()]);
    bundle.value = skillBundle;
    templates.value = templateList;
    for (const skill of skillBundle.skills) {
      if (skill.skillKey) {
        skillContent[skill.skillKey] = skill.content || '';
      }
    }
    keywords.value = templateList.find((t) => t.skillKeywords)?.skillKeywords || '';
    if (!previewTemplateId.value && templateList.length) {
      previewTemplateId.value = templateList.find((t) => t.isDefault)?.id || templateList[0].id!;
    }
  } finally {
    loading.value = false;
  }
}

async function handleSave() {
  saving.value = true;
  try {
    const skills = skillKeyOrder.value.map((key, index) => {
      const existing = bundle.value.skills.find((s) => s.skillKey === key);
      return {
        ...existing,
        skillKey: key,
        skillName: bundle.value.skillKeys[key],
        content: skillContent[key] || '',
        sortOrder: index,
      };
    });
    await skillApi.save({ skills, keywords: keywords.value });
    ElMessage.success('保存成功，各模板技能版本已重新生成');
    await loadData();
  } finally {
    saving.value = false;
  }
}

async function handleRegenerate() {
  saving.value = true;
  try {
    await skillApi.regenerate();
    ElMessage.success('已重新生成 100/200/300/500 字与完整版本');
    await loadData();
  } finally {
    saving.value = false;
  }
}

onMounted(loadData);
</script>

<style scoped>
.char-count {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
  flex: 0 0 auto;
}
.preview-meta {
  color: #606266;
  margin-bottom: 8px;
}
</style>
