<template>
  <div>
    <h2>技能信息管理</h2>
    <el-button type="primary" @click="openDialog()" style="margin-bottom: 16px">新增技能</el-button>
    <el-table :data="list" v-loading="loading" border>
      <el-table-column prop="skillName" label="技能名称" />
      <el-table-column prop="level" label="掌握程度" width="120" />
      <el-table-column prop="category" label="分类" width="120" />
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button size="small" @click="openDialog(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editingForm.id ? '编辑技能' : '新增技能'" width="500px">
      <el-form :model="editingForm" label-width="100px">
        <el-form-item label="技能名称"><el-input v-model="editingForm.skillName" /></el-form-item>
        <el-form-item label="掌握程度">
          <el-select v-model="editingForm.level">
            <el-option label="了解" value="了解" />
            <el-option label="熟练" value="熟练" />
            <el-option label="精通" value="精通" />
          </el-select>
        </el-form-item>
        <el-form-item label="分类"><el-input v-model="editingForm.category" placeholder="如 编程语言、框架、工具" /></el-form-item>
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
import { profileApi, type SkillProfileDTO } from '@/api/profile';

const list = ref<SkillProfileDTO[]>([]);
const loading = ref(false);
const saving = ref(false);
const dialogVisible = ref(false);
const editingForm = reactive<SkillProfileDTO>({});

async function loadData() {
  loading.value = true;
  try {
    const profile = await profileApi.getProfile();
    list.value = profile.skillList || [];
  } finally {
    loading.value = false;
  }
}

function openDialog(row?: SkillProfileDTO) {
  Object.keys(editingForm).forEach((k) => delete (editingForm as any)[k]);
  if (row) {
    Object.assign(editingForm, row);
  } else {
    editingForm.sortOrder = 0;
  }
  dialogVisible.value = true;
}

async function handleSave() {
  saving.value = true;
  try {
    await profileApi.saveSkill(editingForm);
    ElMessage.success('保存成功');
    dialogVisible.value = false;
    await loadData();
  } finally {
    saving.value = false;
  }
}

async function handleDelete(id: number) {
  await ElMessageBox.confirm('确定删除该技能？', '提示');
  await profileApi.deleteSkill(id);
  ElMessage.success('删除成功');
  await loadData();
}

onMounted(loadData);
</script>
