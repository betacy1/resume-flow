<template>
  <div>
    <h2>字段管理</h2>
    <div style="display:flex;gap:12px;align-items:center;margin-bottom:16px;flex-wrap:wrap;">
      <el-input v-model="query.keyword" placeholder="搜索字段名称或Key" style="width:220px;" clearable @change="loadData" />
      <el-select v-model="query.category" placeholder="分类筛选" clearable style="width:180px;" @change="loadData">
        <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
      </el-select>
      <el-select v-model="query.templateId" placeholder="模板筛选" clearable style="width:180px;" @change="loadData">
        <el-option label="全局字段" :value="0" />
        <el-option v-for="t in templates" :key="t.id" :label="t.name || ''" :value="t.id || 0" />
      </el-select>
      <el-button type="primary" @click="openDialog()">新增字段</el-button>
    </div>

    <el-table :data="list" v-loading="loading" border>
      <el-table-column prop="fieldName" label="字段名称" width="120" />
      <el-table-column prop="fieldKey" label="fieldKey" width="140" />
      <el-table-column prop="fieldType" label="类型" width="100" />
      <el-table-column prop="fieldCategory" label="分类" width="120" />
      <el-table-column label="匹配关键词">
        <template #default="{ row }">{{ (row.matchKeywords || []).join(' / ') }}</template>
      </el-table-column>
      <el-table-column label="敏感" width="80">
        <template #default="{ row }"><el-tag :type="row.sensitive ? 'danger' : 'info'">{{ row.sensitive ? '是' : '否' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="启用" width="80">
        <template #default="{ row }">
          <el-switch :model-value="row.enabled" @change="(v:boolean)=>toggleEnabled(row, v)" />
        </template>
      </el-table-column>
      <el-table-column prop="sortOrder" label="排序" width="70" />
      <el-table-column label="操作" width="170">
        <template #default="{ row }">
          <el-button size="small" @click="openDialog(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row.id!)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editingForm.id ? '编辑字段' : '新增字段'" width="760px">
      <el-form :model="editingForm" label-width="120px">
        <el-form-item label="字段名称"><el-input v-model="editingForm.fieldName" /></el-form-item>
        <el-form-item label="字段 Key"><el-input v-model="editingForm.fieldKey" /></el-form-item>
        <el-form-item label="字段类型">
          <el-select v-model="editingForm.fieldType">
            <el-option label="input" value="input" />
            <el-option label="textarea" value="textarea" />
            <el-option label="select" value="select" />
            <el-option label="richText" value="richText" />
          </el-select>
        </el-form-item>
        <el-form-item label="字段分类">
          <el-select v-model="editingForm.fieldCategory" allow-create filterable>
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="岗位模板">
          <el-select v-model="editingForm.templateId" clearable placeholder="空=全局字段">
            <el-option v-for="t in templates" :key="t.id" :label="t.name || ''" :value="t.id || 0" />
          </el-select>
        </el-form-item>
        <el-form-item label="字段内容"><el-input v-model="editingForm.fieldValue" type="textarea" :rows="5" /></el-form-item>
        <el-form-item label="匹配关键词">
          <el-select
            v-model="editingKeywords"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="输入关键词后回车添加"
            style="width:100%;"
          />
        </el-form-item>
        <el-form-item label="敏感字段"><el-switch v-model="editingForm.sensitive" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="editingForm.enabled" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="editingForm.sortOrder" :min="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { customFieldApi, templateApi, type UserCustomFieldDTO, type ApplicationTemplateDTO } from '@/api/template';

const categories = ['基础信息', '教育经历', '实习经历', '项目经历', '开放题', '其他'];
const list = ref<UserCustomFieldDTO[]>([]);
const templates = ref<ApplicationTemplateDTO[]>([]);
const loading = ref(false);
const saving = ref(false);
const dialogVisible = ref(false);
const query = reactive<{ keyword?: string; category?: string; templateId?: number }>({});
const editingForm = reactive<UserCustomFieldDTO>({});
const editingKeywords = ref<string[]>([]);

async function loadTemplates() {
  templates.value = await templateApi.list();
}

async function loadData() {
  loading.value = true;
  try {
    list.value = await customFieldApi.list({
      keyword: query.keyword || undefined,
      category: query.category || undefined,
      templateId: query.templateId ? query.templateId : undefined,
    });
  } finally {
    loading.value = false;
  }
}

function openDialog(row?: UserCustomFieldDTO) {
  Object.keys(editingForm).forEach((k) => delete (editingForm as any)[k]);
  editingKeywords.value = [];
  if (row) {
    Object.assign(editingForm, row);
    editingKeywords.value = [...(row.matchKeywords || [])];
  } else {
    Object.assign(editingForm, {
      fieldType: 'input',
      fieldCategory: '基础信息',
      sensitive: false,
      enabled: true,
      sortOrder: 0,
    });
  }
  dialogVisible.value = true;
}

async function handleSave() {
  saving.value = true;
  try {
    editingForm.matchKeywords = editingKeywords.value.filter((item) => item.trim().length > 0);
    if (editingForm.templateId === 0) editingForm.templateId = undefined;
    if (editingForm.id) {
      await customFieldApi.update(editingForm.id, editingForm);
    } else {
      await customFieldApi.create(editingForm);
    }
    ElMessage.success('保存成功');
    dialogVisible.value = false;
    await loadData();
  } finally {
    saving.value = false;
  }
}

async function handleDelete(id: number) {
  await ElMessageBox.confirm('确定删除该字段？', '提示');
  await customFieldApi.delete(id);
  ElMessage.success('删除成功');
  await loadData();
}

async function toggleEnabled(row: UserCustomFieldDTO, enabled: boolean) {
  await customFieldApi.setEnabled(row.id!, enabled);
  row.enabled = enabled;
}

onMounted(async () => {
  await Promise.all([loadTemplates(), loadData()]);
});
</script>
