<template>
  <div>
    <h2>素材库管理</h2>
    <div style="display:flex;gap:12px;align-items:center;margin-bottom:16px;flex-wrap:wrap;">
      <el-select v-model="query.materialType" clearable placeholder="按类型筛选" style="width:180px" @change="loadData">
        <el-option v-for="(label, value) in typeLabels" :key="value" :label="label" :value="value" />
      </el-select>
      <el-select v-model="query.templateId" clearable placeholder="按岗位模板筛选" style="width:200px" @change="loadData">
        <el-option v-for="t in templates" :key="t.id" :label="t.name || ''" :value="t.id || 0" />
      </el-select>
      <el-input v-model="keyword" clearable placeholder="按标题/内容搜索" style="width:220px" />
      <el-button type="primary" @click="openDialog()">新增素材</el-button>
    </div>

    <el-table :data="filteredList" v-loading="loading" border>
      <el-table-column prop="title" label="标题" width="170" />
      <el-table-column label="类型" width="140">
        <template #default="{ row }">{{ typeLabels[row.materialType || ''] || row.materialType }}</template>
      </el-table-column>
      <el-table-column prop="wordLimitType" label="字数版本" width="110" />
      <el-table-column label="字数" width="80">
        <template #default="{ row }">{{ (row.content || '').length }}</template>
      </el-table-column>
      <el-table-column label="模板" width="150">
        <template #default="{ row }">{{ templateName(row.templateId) }}</template>
      </el-table-column>
      <el-table-column label="启用" width="70">
        <template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '是' : '否' }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="updateTime" label="更新时间" width="180" />
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button size="small" @click="openDialog(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row.id!)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editingForm.id ? '编辑素材' : '新增素材'" width="740px">
      <el-form :model="editingForm" label-width="100px">
        <el-form-item label="素材标题"><el-input v-model="editingForm.title" /></el-form-item>
        <el-form-item label="素材类型">
          <el-select v-model="editingForm.materialType">
            <el-option v-for="(label, value) in typeLabels" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="岗位模板">
          <el-select v-model="editingForm.templateId" clearable placeholder="空=通用素材">
            <el-option v-for="t in templates" :key="t.id" :label="t.name || ''" :value="t.id || 0" />
          </el-select>
        </el-form-item>
        <el-form-item label="字数版本"><el-input v-model="editingForm.wordLimitType" placeholder="例如 200字 / 500字 / 1000字" /></el-form-item>
        <el-form-item label="简称"><el-input v-model="editingForm.shortName" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="editingForm.enabled" /></el-form-item>
        <el-form-item label="内容">
          <el-input v-model="editingForm.content" type="textarea" :rows="10" />
          <div style="color:#909399;font-size:12px;margin-top:4px">{{ (editingForm.content || '').length }} 字</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { materialApi, templateApi, type AnswerMaterialDTO, type ApplicationTemplateDTO } from '@/api/template';

const typeLabels: Record<string, string> = {
  SELF_EVALUATION: '自我评价',
  INTERNSHIP: '实习经历',
  PROJECT: '项目经历',
  AI_COLLABORATION: 'AI协作经历',
  CAREER_PLAN: '职业规划',
  HOBBY: '兴趣特长',
  WHY_COMPANY: '为什么选择本公司',
  WHY_POSITION: '为什么选择本岗位',
  SUPPLEMENT: '补充信息',
  AI_TOOL_USAGE: 'AI工具使用',
  PROJECT_CHALLENGE: '项目挑战',
  TEAM_COLLABORATION: '团队协作',
  STRESS_RESISTANCE: '抗压能力',
  WHY_BANK: '为什么选择银行',
  WHY_STATE_OWNED: '为什么选择国央企',
  WHY_INTERNET: '为什么选择互联网',
  BEST_PROJECT: '最有成就感的项目',
  HARDEST_PROBLEM: '最困难的问题',
  INTERNSHIP_GAINS: '实习收获',
  TECH_INTEREST: '技术兴趣',
  PERSONAL_STRENGTH: '个人优势',
};

/** 常见英文技术词标准写法（质量检查：仅提醒不阻断） */
const TECH_TERMS: Array<[string, string]> = [
  ['javascript', 'JavaScript'], ['typescript', 'TypeScript'], ['spring boot', 'Spring Boot'],
  ['springboot', 'Spring Boot'], ['mysql', 'MySQL'], ['redis', 'Redis'],
  ['docker', 'Docker'], ['kubernetes', 'Kubernetes'], ['python', 'Python'],
  ['elasticsearch', 'Elasticsearch'], ['mongodb', 'MongoDB'], ['java', 'Java'],
  ['vue', 'Vue'], ['react', 'React'], ['github', 'GitHub'], ['linux', 'Linux'],
];

function checkQuality(content: string, wordLimitType?: string): string[] {
  const warnings: string[] = [];
  const m = (wordLimitType || '').match(/(\d{2,4})/);
  if (m && content.length > Number(m[1])) {
    warnings.push(`内容共 ${content.length} 字，超过字数版本 ${wordLimitType} 的上限 ${m[1]} 字`);
  }
  for (const [lower, canonical] of TECH_TERMS) {
    const re = new RegExp(`(?<![A-Za-z])${lower.replace(/ /g, '\\s*')}(?![A-Za-z])`, 'i');
    const hit = content.match(re);
    if (hit && hit[0] !== canonical) {
      warnings.push(`发现“${hit[0]}”，建议写作“${canonical}”`);
      if (warnings.length >= 3) break;
    }
  }
  return warnings;
}

const list = ref<AnswerMaterialDTO[]>([]);
const templates = ref<ApplicationTemplateDTO[]>([]);
const query = reactive<{ materialType?: string; templateId?: number }>({});
const keyword = ref('');
const loading = ref(false);
const saving = ref(false);
const dialogVisible = ref(false);
const editingForm = reactive<AnswerMaterialDTO>({});

/** 前端关键词过滤（标题/内容/简称） */
const filteredList = computed(() => {
  const kw = keyword.value.trim().toLowerCase();
  if (!kw) return list.value;
  return list.value.filter((m) =>
    [m.title, m.content, m.shortName].some((s) => (s || '').toLowerCase().includes(kw)),
  );
});

function templateName(templateId?: number) {
  if (!templateId) return '通用';
  return templates.value.find((t) => t.id === templateId)?.name || `模板#${templateId}`;
}

async function loadData() {
  loading.value = true;
  try {
    list.value = await materialApi.list({
      materialType: query.materialType || undefined,
      templateId: query.templateId ? query.templateId : undefined,
    });
  } finally {
    loading.value = false;
  }
}

async function loadTemplates() {
  templates.value = await templateApi.list();
}

function openDialog(row?: AnswerMaterialDTO) {
  Object.keys(editingForm).forEach((k) => delete (editingForm as any)[k]);
  if (row) {
    Object.assign(editingForm, row);
  } else {
    Object.assign(editingForm, {
      materialType: 'SELF_EVALUATION',
      wordLimitType: '500字',
      enabled: true,
      sortOrder: 0,
    });
  }
  dialogVisible.value = true;
}

async function handleSave() {
  // 质量检查：空内容阻断；超字数/英文大小写仅提醒不阻断
  if (!editingForm.title?.trim() || !editingForm.content?.trim()) {
    ElMessage.error('标题与内容不能为空');
    return;
  }
  const warnings = checkQuality(editingForm.content, editingForm.wordLimitType);
  saving.value = true;
  try {
    if (editingForm.templateId === 0) editingForm.templateId = undefined;
    if (editingForm.id) {
      await materialApi.update(editingForm.id, editingForm);
    } else {
      await materialApi.create(editingForm);
    }
    if (warnings.length) {
      ElMessage.warning(`保存成功，但请注意：${warnings.join('；')}`);
    } else {
      ElMessage.success('保存成功');
    }
    dialogVisible.value = false;
    await loadData();
  } finally {
    saving.value = false;
  }
}

async function handleDelete(id: number) {
  await ElMessageBox.confirm('确定删除该素材？', '提示');
  await materialApi.delete(id);
  ElMessage.success('删除成功');
  await loadData();
}

onMounted(async () => {
  await Promise.all([loadTemplates(), loadData()]);
});
</script>
