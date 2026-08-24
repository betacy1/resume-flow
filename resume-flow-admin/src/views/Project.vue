<template>
  <div>
    <h2>项目经历管理</h2>
    <el-button type="primary" @click="openDialog()" style="margin-bottom: 16px">新增项目经历</el-button>
    <el-table :data="list" v-loading="loading" border>
      <el-table-column prop="projectName" label="项目名称" />
      <el-table-column prop="role" label="角色" width="100" />
      <el-table-column prop="shortName" label="简称" width="80" />
      <el-table-column prop="startDate" label="开始时间" width="120" />
      <el-table-column prop="endDate" label="结束时间" width="120" />
      <el-table-column label="默认" width="60">
        <template #default="{ row }">
          <el-tag v-if="row.isDefault" type="success">是</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220">
        <template #default="{ row }">
          <el-button size="small" @click="openDialog(row)">编辑</el-button>
          <el-button size="small" type="primary" @click="goVariants(row)">版本</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editingForm.id ? '编辑项目经历' : '新增项目经历'" width="680px">
      <el-form :model="editingForm" label-width="100px">
        <el-form-item label="项目名称"><el-input v-model="editingForm.projectName" /></el-form-item>
        <el-form-item label="角色"><el-input v-model="editingForm.role" /></el-form-item>
        <el-form-item label="简称"><el-input v-model="editingForm.shortName" /></el-form-item>
        <el-form-item label="开始时间"><el-input v-model="editingForm.startDate" placeholder="标准格式如 2026-05-08" /></el-form-item>
        <el-form-item label="结束时间"><el-input v-model="editingForm.endDate" placeholder="标准格式如 2026-07-03" /></el-form-item>
        <el-form-item label="是否默认"><el-switch v-model="editingForm.isDefault" /></el-form-item>
        <el-form-item label="技术栈"><el-input v-model="editingForm.techStack" /></el-form-item>
        <el-form-item label="项目简介"><el-input v-model="editingForm.projectIntro" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="职责内容"><el-input v-model="editingForm.responsibilities" type="textarea" :rows="5" /></el-form-item>
        <el-form-item label="项目成果"><el-input v-model="editingForm.result" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="汇总描述"><el-input v-model="editingForm.description" type="textarea" :rows="4" placeholder="用于普通文本框填写的完整描述" /></el-form-item>
        <el-form-item label="模板展示">
          <div style="color: #909399; font-size: 12px">各模板下的展示/自动填充/优先级请在“岗位模板管理 → 经历配置”中设置，项目经历本身不会被删除或排除</div>
        </el-form-item>
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
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { profileApi, type ProjectExperienceDTO } from '@/api/profile';

const router = useRouter();

const list = ref<ProjectExperienceDTO[]>([]);
const loading = ref(false);
const saving = ref(false);
const dialogVisible = ref(false);
const editingForm = reactive<ProjectExperienceDTO>({});

async function loadData() {
  loading.value = true;
  try {
    const profile = await profileApi.getProfile();
    list.value = profile.projectList || [];
  } finally {
    loading.value = false;
  }
}

function openDialog(row?: ProjectExperienceDTO) {
  Object.keys(editingForm).forEach((k) => delete (editingForm as any)[k]);
  if (row) {
    Object.assign(editingForm, row);
  } else {
    editingForm.isDefault = false;
    editingForm.sortOrder = 0;
  }
  dialogVisible.value = true;
}

async function handleSave() {
  saving.value = true;
  try {
    await profileApi.saveProject(editingForm);
    ElMessage.success('保存成功');
    dialogVisible.value = false;
    await loadData();
  } finally {
    saving.value = false;
  }
}

function goVariants(row: ProjectExperienceDTO) {
  router.push({ path: '/variants', query: { sourceType: 'project', sourceId: String(row.id) } });
}

async function handleDelete(id: number) {
  await ElMessageBox.confirm('确定删除该项目经历？', '提示');
  await profileApi.deleteProject(id);
  ElMessage.success('删除成功');
  await loadData();
}

onMounted(loadData);
</script>
