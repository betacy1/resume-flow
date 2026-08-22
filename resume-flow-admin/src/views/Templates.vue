<template>
  <div>
    <h2>岗位模板管理</h2>
    <el-button type="primary" @click="openDialog()" style="margin-bottom: 16px">新增岗位模板</el-button>
    <el-table :data="list" v-loading="loading" border>
      <el-table-column prop="name" label="模板名称" />
      <el-table-column prop="category" label="分类" width="120" />
      <el-table-column label="受众风格" width="110">
        <template #default="{ row }">{{ audienceLabel(row.audienceType) }}</template>
      </el-table-column>
      <el-table-column label="默认" width="60">
        <template #default="{ row }">
          <el-tag v-if="row.isDefault" type="success">是</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button size="small" @click="openDialog(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editingForm.id ? '编辑岗位模板' : '新增岗位模板'" width="700px">
      <el-form :model="editingForm" label-width="120px">
        <el-form-item label="模板名称"><el-input v-model="editingForm.name" /></el-form-item>
        <el-form-item label="分类">
          <el-select v-model="editingForm.category" allow-create filterable>
            <el-option label="后端开发版" value="后端开发版" />
            <el-option label="AI 应用工程化版" value="AI 应用工程化版" />
            <el-option label="金融科技版" value="金融科技版" />
            <el-option label="国企央企版" value="国企央企版" />
            <el-option label="自定义" value="自定义" />
          </el-select>
        </el-form-item>
        <el-form-item label="受众风格">
          <el-select v-model="editingForm.audienceType" placeholder="决定插件选择的内容版本风格">
            <el-option label="大厂互联网版 big_tech" value="big_tech" />
            <el-option label="国央企版 state_owned" value="state_owned" />
            <el-option label="银行金融科技版 bank" value="bank" />
            <el-option label="通用后端版 general_backend" value="general_backend" />
          </el-select>
        </el-form-item>
        <el-form-item label="模板说明"><el-input v-model="editingForm.description" type="textarea" :rows="2" placeholder="适用公司与语言风格说明" /></el-form-item>
        <el-form-item label="是否默认"><el-switch v-model="editingForm.isDefault" /></el-form-item>
        <el-form-item label="自我评价"><el-input v-model="editingForm.selfEvaluation" type="textarea" :rows="4" /></el-form-item>
        <el-form-item label="实习经历描述"><el-input v-model="editingForm.internshipDescription" type="textarea" :rows="4" /></el-form-item>
        <el-form-item label="项目经历描述"><el-input v-model="editingForm.projectDescription" type="textarea" :rows="4" /></el-form-item>
        <el-form-item label="职业规划"><el-input v-model="editingForm.careerPlan" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="AI 协作经历"><el-input v-model="editingForm.aiCollaboration" type="textarea" :rows="4" /></el-form-item>
        <el-form-item label="技能关键词"><el-input v-model="editingForm.skillKeywords" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { templateApi, type ApplicationTemplateDTO } from '@/api/template';

const list = ref<ApplicationTemplateDTO[]>([]);
const loading = ref(false);
const saving = ref(false);
const dialogVisible = ref(false);
const editingForm = reactive<ApplicationTemplateDTO>({});

const audienceLabels: Record<string, string> = {
  big_tech: '大厂版',
  state_owned: '国央企版',
  bank: '银行版',
  general_backend: '通用后端版',
};

function audienceLabel(type?: string) {
  return (type && audienceLabels[type]) || type || '-';
}

async function loadData() {
  loading.value = true;
  try {
    list.value = await templateApi.list();
  } finally {
    loading.value = false;
  }
}

function openDialog(row?: ApplicationTemplateDTO) {
  Object.keys(editingForm).forEach((k) => delete (editingForm as any)[k]);
  if (row) {
    Object.assign(editingForm, row);
  } else {
    editingForm.isDefault = false;
    editingForm.category = '自定义';
  }
  dialogVisible.value = true;
}

async function handleSave() {
  saving.value = true;
  try {
    if (editingForm.id) {
      await templateApi.update(editingForm.id, editingForm);
    } else {
      await templateApi.create(editingForm);
    }
    ElMessage.success('保存成功');
    dialogVisible.value = false;
    await loadData();
  } finally {
    saving.value = false;
  }
}

async function handleDelete(id: number) {
  await ElMessageBox.confirm('确定删除该岗位模板？', '提示');
  await templateApi.delete(id);
  ElMessage.success('删除成功');
  await loadData();
}

onMounted(loadData);
</script>
