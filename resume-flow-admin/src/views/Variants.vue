<template>
  <div>
    <h2>内容版本管理</h2>
    <p style="color: #909399; margin-bottom: 12px">
      每条实习/项目/素材按 场景风格 × 岗位方向 × 字段类型 × 字数限制 维护内容版本，
      插件按当前模板、岗位方向与页面字段类型/字数自动选择；大厂版不包含工行相关实习与项目。
    </p>

    <el-card style="margin-bottom: 16px">
      <el-form inline>
        <el-form-item label="来源类型">
          <el-select v-model="sourceType" @change="loadSources" style="width: 140px">
            <el-option label="实习经历" value="internship" />
            <el-option label="项目经历" value="project" />
            <el-option label="开放题素材" value="material" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="sourceId" @change="loadVariants" style="width: 320px" placeholder="请选择">
            <el-option v-for="s in sources" :key="s.id" :label="s.label" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="场景">
          <el-select v-model="filterAudience" style="width: 120px" clearable>
            <el-option v-for="(label, key) in audienceLabels" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="岗位方向">
          <el-select v-model="filterDirection" style="width: 140px" clearable>
            <el-option v-for="(label, key) in directionLabels" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="字段类型">
          <el-select v-model="filterFieldType" style="width: 150px" clearable>
            <el-option v-for="(label, key) in fieldTypeLabels" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="字数">
          <el-select v-model="filterLength" style="width: 120px" clearable>
            <el-option v-for="(label, key) in lengthLabels" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
      </el-form>
    </el-card>

    <el-table :data="filteredVariants" v-loading="loading" border>
      <el-table-column label="受众风格" width="100">
        <template #default="{ row }">
          <el-tag :type="audienceTagType(row.audienceType)">{{ audienceLabel(row.audienceType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="岗位方向" width="100">
        <template #default="{ row }">{{ directionLabel(row.jobDirection) }}</template>
      </el-table-column>
      <el-table-column label="字段类型" width="110">
        <template #default="{ row }">{{ fieldTypeLabel(row.fieldType) }}</template>
      </el-table-column>
      <el-table-column label="长度" width="110">
        <template #default="{ row }">{{ lengthLabel(row.lengthType) }}</template>
      </el-table-column>
      <el-table-column label="内容" min-width="360">
        <template #default="{ row }">
          <span class="variant-content">{{ (row.content || '').slice(0, 100) }}{{ (row.content || '').length > 100 ? '…' : '' }}</span>
          <div style="color: #c0c4cc; font-size: 12px">{{ (row.content || '').length }} 字</div>
        </template>
      </el-table-column>
      <el-table-column label="启用" width="70">
        <template #default="{ row }">
          <el-tag v-if="row.enabled" type="success">是</el-tag>
          <el-tag v-else type="info">否</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button size="small" @click="openDialog(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" title="编辑内容版本" width="680px">
      <el-form :model="editingForm" label-width="90px">
        <el-form-item label="受众风格">
          <span>{{ audienceLabel(editingForm.audienceType) }}</span>
        </el-form-item>
        <el-form-item label="岗位方向">
          <span>{{ directionLabel(editingForm.jobDirection) }}</span>
        </el-form-item>
        <el-form-item label="字段类型">
          <span>{{ fieldTypeLabel(editingForm.fieldType) }}</span>
        </el-form-item>
        <el-form-item label="长度">
          <span>{{ lengthLabel(editingForm.lengthType) }}</span>
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="editingForm.content" type="textarea" :rows="10" />
        </el-form-item>
        <el-form-item label="启用"><el-switch v-model="editingForm.enabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { profileApi } from '@/api/profile';
import { materialApi } from '@/api/template';
import { variantApi, type ContentVariant } from '@/api/variant';

const route = useRoute();

const sourceType = ref<string>('internship');
const sourceId = ref<number | undefined>(undefined);
const sources = ref<{ id: number; label: string }[]>([]);
const variants = ref<ContentVariant[]>([]);
const filterAudience = ref<string>('');
const filterDirection = ref<string>('');
const filterFieldType = ref<string>('');
const filterLength = ref<string>('');
const loading = ref(false);
const saving = ref(false);
const dialogVisible = ref(false);
const editingForm = reactive<ContentVariant>({});

const audienceLabels: Record<string, string> = {
  big_tech: '大厂版',
  state_owned: '国央企版',
  bank: '银行版',
  general: '通用版',
};
const directionLabels: Record<string, string> = {
  backend: '后端开发',
  ai: 'AI 应用工程化',
  fintech: '金融科技',
  general: '通用',
};
const fieldTypeLabels: Record<string, string> = {
  internship_overview: '实习描述',
  internship_responsibility: '主要职责',
  internship_result: '工作成果',
  internship_tech_stack: '技术栈',
  internship_combined: '合并型',
  project_overview: '项目描述',
  project_responsibility: '项目职责',
  project_result: '项目成果',
  project_tech_stack: '项目技术栈',
  project_combined: '项目合并型',
  combined: '合并型',
};
const lengthLabels: Record<string, string> = {
  within_200: '200字以内',
  within_300: '300字以内',
  within_500: '500字以内',
  within_1000: '1000字以内',
};

function audienceLabel(type?: string) {
  return (type && audienceLabels[type]) || type || '-';
}
function directionLabel(type?: string) {
  return (type && directionLabels[type]) || type || '-';
}
function fieldTypeLabel(type?: string) {
  return (type && fieldTypeLabels[type]) || type || '-';
}
function lengthLabel(type?: string) {
  return (type && lengthLabels[type]) || type || '-';
}

const filteredVariants = computed(() =>
  variants.value.filter((v) => {
    if (filterAudience.value && v.audienceType !== filterAudience.value) return false;
    if (filterDirection.value && v.jobDirection !== filterDirection.value) return false;
    if (filterFieldType.value && v.fieldType !== filterFieldType.value) return false;
    if (filterLength.value && v.lengthType !== filterLength.value) return false;
    return true;
  }),
);
function audienceTagType(type?: string) {
  if (type === 'big_tech') return 'primary';
  if (type === 'state_owned') return 'warning';
  if (type === 'bank') return 'danger';
  return 'info';
}

async function loadSources() {
  sourceId.value = undefined;
  variants.value = [];
  sources.value = [];
  if (sourceType.value === 'internship') {
    const profile = await profileApi.getProfile();
    sources.value = (profile.internshipList || []).map((i) => ({
      id: i.id as number,
      label: `${i.company} - ${i.position || ''}`.trim(),
    }));
  } else if (sourceType.value === 'project') {
    const profile = await profileApi.getProfile();
    sources.value = (profile.projectList || []).map((p) => ({
      id: p.id as number,
      label: p.projectName || String(p.id),
    }));
  } else {
    const list = await materialApi.list();
    sources.value = (list || []).map((m) => ({ id: m.id as number, label: m.title || String(m.id) }));
  }
  if (sources.value.length > 0) {
    sourceId.value = sources.value[0].id;
    await loadVariants();
  }
}

async function loadVariants() {
  if (!sourceId.value) return;
  loading.value = true;
  try {
    const list = await variantApi.list(sourceType.value, sourceId.value);
    const audienceOrder = ['big_tech', 'state_owned', 'bank', 'general'];
    const directionOrder = ['backend', 'ai', 'fintech', 'general'];
    const fieldTypeOrder = ['internship_overview', 'internship_responsibility', 'internship_result',
      'internship_tech_stack', 'internship_combined', 'project_overview', 'project_responsibility',
      'project_result', 'project_tech_stack', 'project_combined', 'combined'];
    const lengthOrder = ['within_200', 'within_300', 'within_500', 'within_1000'];
    variants.value = [...list].sort((a, b) => {
      const ai = audienceOrder.indexOf(a.audienceType || '');
      const bi = audienceOrder.indexOf(b.audienceType || '');
      if (ai !== bi) return ai - bi;
      const di = directionOrder.indexOf(a.jobDirection || '');
      const dj = directionOrder.indexOf(b.jobDirection || '');
      if (di !== dj) return di - dj;
      const fi = fieldTypeOrder.indexOf(a.fieldType || '');
      const fj = fieldTypeOrder.indexOf(b.fieldType || '');
      if (fi !== fj) return fi - fj;
      return lengthOrder.indexOf(a.lengthType || '') - lengthOrder.indexOf(b.lengthType || '');
    });
  } finally {
    loading.value = false;
  }
}

function openDialog(row: ContentVariant) {
  Object.keys(editingForm).forEach((k) => delete (editingForm as any)[k]);
  Object.assign(editingForm, row);
  dialogVisible.value = true;
}

async function handleSave() {
  saving.value = true;
  try {
    await variantApi.save(editingForm);
    ElMessage.success('保存成功');
    dialogVisible.value = false;
    await loadVariants();
  } finally {
    saving.value = false;
  }
}

async function handleDelete(id?: number) {
  if (!id) return;
  await ElMessageBox.confirm('确定删除该内容版本？', '提示');
  await variantApi.delete(id);
  ElMessage.success('删除成功');
  await loadVariants();
}

onMounted(async () => {
  const qType = route.query.sourceType as string | undefined;
  const qId = route.query.sourceId as string | undefined;
  if (qType && ['internship', 'project', 'material'].includes(qType)) {
    sourceType.value = qType;
  }
  await loadSources();
  if (qId) {
    sourceId.value = Number(qId);
    await loadVariants();
  }
});
</script>

<style scoped>
.variant-content {
  white-space: pre-wrap;
}
</style>
