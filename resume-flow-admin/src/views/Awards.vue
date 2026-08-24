<template>
  <div>
    <h2>奖项荣誉管理</h2>
    <el-button type="primary" @click="openDialog()" style="margin-bottom: 16px">新增奖项荣誉</el-button>
    <el-table :data="list" v-loading="loading" border>
      <el-table-column prop="awardName" label="奖项名称" />
      <el-table-column prop="awardType" label="类型" width="120" />
      <el-table-column prop="awardYear" label="获得时间" width="120" />
      <el-table-column prop="awardLevel" label="级别" width="100" />
      <el-table-column prop="description" label="说明" />
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button size="small" @click="openDialog(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editingForm.id ? '编辑奖项荣誉' : '新增奖项荣誉'" width="520px">
      <el-form :model="editingForm" label-width="100px">
        <el-form-item label="奖项名称"><el-input v-model="editingForm.awardName" placeholder="如 北京理工大学研究生学业一等奖学金" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="editingForm.awardType" allow-create filterable placeholder="请选择或输入">
            <el-option label="奖项" value="奖项" />
            <el-option label="奖学金" value="奖学金" />
            <el-option label="专利成果" value="专利成果" />
            <el-option label="荣誉称号" value="荣誉称号" />
            <el-option label="竞赛奖项" value="竞赛奖项" />
            <el-option label="证书" value="证书" />
          </el-select>
        </el-form-item>
        <el-form-item label="获得时间"><el-input v-model="editingForm.awardYear" placeholder="如 2025.11 / 2025-03-26" /></el-form-item>
        <el-form-item label="级别">
          <el-select v-model="editingForm.awardLevel" allow-create filterable placeholder="请选择或输入">
            <el-option label="院校级" value="院校级" />
            <el-option label="省部级" value="省部级" />
            <el-option label="国家级" value="国家级" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明"><el-input v-model="editingForm.description" type="textarea" :rows="3" /></el-form-item>
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
import { profileApi, type AwardCertificateDTO } from '@/api/profile';

const list = ref<AwardCertificateDTO[]>([]);
const loading = ref(false);
const saving = ref(false);
const dialogVisible = ref(false);
const editingForm = reactive<AwardCertificateDTO>({});

async function loadData() {
  loading.value = true;
  try {
    const profile = await profileApi.getProfile();
    list.value = profile.awardList || [];
  } finally {
    loading.value = false;
  }
}

function openDialog(row?: AwardCertificateDTO) {
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
    await profileApi.saveAward(editingForm);
    ElMessage.success('保存成功');
    dialogVisible.value = false;
    await loadData();
  } finally {
    saving.value = false;
  }
}

async function handleDelete(id?: number) {
  if (!id) return;
  await ElMessageBox.confirm('确定删除该奖项荣誉？', '提示');
  await profileApi.deleteAward(id);
  ElMessage.success('删除成功');
  await loadData();
}

onMounted(loadData);
</script>
