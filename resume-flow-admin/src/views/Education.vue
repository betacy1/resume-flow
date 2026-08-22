<template>
  <div>
    <h2>教育经历管理</h2>
    <el-button type="primary" @click="openDialog()" style="margin-bottom: 16px">新增教育经历</el-button>
    <el-table :data="list" v-loading="loading" border>
      <el-table-column prop="school" label="学校" />
      <el-table-column prop="major" label="专业" />
      <el-table-column prop="degree" label="学历" width="80" />
      <el-table-column prop="startDate" label="开始时间" width="120" />
      <el-table-column prop="endDate" label="结束时间" width="120" />
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

    <el-dialog v-model="dialogVisible" :title="editingForm.id ? '编辑教育经历' : '新增教育经历'" width="640px">
      <el-form :model="editingForm" label-width="100px">
        <el-form-item label="学校"><el-input v-model="editingForm.school" /></el-form-item>
        <el-form-item label="学校标签"><el-input v-model="editingForm.schoolTags" placeholder="如 985、211、双一流" /></el-form-item>
        <el-form-item label="专业"><el-input v-model="editingForm.major" /></el-form-item>
        <el-form-item label="学院"><el-input v-model="editingForm.college" /></el-form-item>
        <el-form-item label="学历">
          <el-select v-model="editingForm.degree">
            <el-option label="大专" value="大专" />
            <el-option label="本科" value="本科" />
            <el-option label="硕士" value="硕士" />
            <el-option label="硕士研究生" value="硕士研究生" />
            <el-option label="博士" value="博士" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始时间"><el-input v-model="editingForm.startDate" placeholder="标准格式如 2021-09-01，插件会按页面要求格式化" /></el-form-item>
        <el-form-item label="结束时间"><el-input v-model="editingForm.endDate" placeholder="标准格式如 2025-06-30" /></el-form-item>
        <el-form-item label="GPA"><el-input v-model="editingForm.gpa" placeholder="如 3.5/4" /></el-form-item>
        <el-form-item label="成绩排名"><el-input v-model="editingForm.rank" placeholder="如 前20%" /></el-form-item>
        <el-form-item label="导师"><el-input v-model="editingForm.advisor" /></el-form-item>
        <el-form-item label="实验室"><el-input v-model="editingForm.lab" /></el-form-item>
        <el-form-item label="研究方向"><el-input v-model="editingForm.researchDirection" /></el-form-item>
        <el-form-item label="毕业论文"><el-input v-model="editingForm.thesis" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="荣誉"><el-input v-model="editingForm.honors" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="是否默认"><el-switch v-model="editingForm.isDefault" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="editingForm.description" type="textarea" :rows="3" /></el-form-item>
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
import { profileApi, type EducationExperienceDTO } from '@/api/profile';

const list = ref<EducationExperienceDTO[]>([]);
const loading = ref(false);
const saving = ref(false);
const dialogVisible = ref(false);
const editingForm = reactive<EducationExperienceDTO>({});

async function loadData() {
  loading.value = true;
  try {
    const profile = await profileApi.getProfile();
    list.value = profile.educationList || [];
  } finally {
    loading.value = false;
  }
}

function openDialog(row?: EducationExperienceDTO) {
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
    await profileApi.saveEducation(editingForm);
    ElMessage.success('保存成功');
    dialogVisible.value = false;
    await loadData();
  } finally {
    saving.value = false;
  }
}

async function handleDelete(id: number) {
  await ElMessageBox.confirm('确定删除该教育经历？', '提示');
  await profileApi.deleteEducation(id);
  ElMessage.success('删除成功');
  await loadData();
}

onMounted(loadData);
</script>
