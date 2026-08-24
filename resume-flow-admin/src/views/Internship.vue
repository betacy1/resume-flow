<template>
  <div>
    <h2>实习经历管理</h2>
    <el-button type="primary" @click="openDialog()" style="margin-bottom: 16px">新增实习经历</el-button>
    <el-table :data="list" v-loading="loading" border>
      <el-table-column prop="company" label="公司" />
      <el-table-column prop="position" label="岗位" />
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

    <el-dialog v-model="dialogVisible" :title="editingForm.id ? '编辑实习经历' : '新增实习经历'" width="640px">
      <el-form :model="editingForm" label-width="100px">
        <el-form-item label="公司"><el-input v-model="editingForm.company" /></el-form-item>
        <el-form-item label="部门"><el-input v-model="editingForm.department" /></el-form-item>
        <el-form-item label="岗位"><el-input v-model="editingForm.position" /></el-form-item>
        <el-form-item label="简称"><el-input v-model="editingForm.shortName" placeholder="如 京东、字节" /></el-form-item>
        <el-form-item label="开始时间"><el-input v-model="editingForm.startDate" placeholder="标准格式如 2026-05-08" /></el-form-item>
        <el-form-item label="结束时间"><el-input v-model="editingForm.endDate" placeholder="标准格式如 2026-07-03" /></el-form-item>
        <el-form-item label="技术栈"><el-input v-model="editingForm.techStack" placeholder="如 Java / Spring Boot / Redis" /></el-form-item>
        <el-form-item label="是否默认"><el-switch v-model="editingForm.isDefault" /></el-form-item>
        <el-form-item label="经历描述"><el-input v-model="editingForm.description" type="textarea" :rows="6" /></el-form-item>
        <el-form-item label="亮点成果"><el-input v-model="editingForm.highlights" type="textarea" :rows="3" /></el-form-item>
        <el-divider content-position="left">证明人信息（跟随本段实习，空字段插件端显示“未填写”）</el-divider>
        <el-form-item label="证明人姓名"><el-input v-model="editingForm.certifierName" /></el-form-item>
        <el-form-item label="证明人单位"><el-input v-model="editingForm.certifierCompany" /></el-form-item>
        <el-form-item label="证明人职务"><el-input v-model="editingForm.certifierPosition" /></el-form-item>
        <el-form-item label="单位及职务"><el-input v-model="editingForm.certifierCompanyAndPosition" placeholder="如 京东集团-京东科技-软件开发工程师（正职）" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="editingForm.certifierPhone" /></el-form-item>
        <el-form-item label="证明人邮箱"><el-input v-model="editingForm.certifierEmail" /></el-form-item>
        <el-form-item label="与本人关系"><el-input v-model="editingForm.certifierRelation" placeholder="如 实习证明人" /></el-form-item>
        <el-form-item label="证明人备注"><el-input v-model="editingForm.certifierRemark" /></el-form-item>
        <el-form-item label="模板展示">
          <div style="color: #909399; font-size: 12px">各模板下的展示/自动填充/优先级请在“岗位模板管理 → 经历配置”中设置，实习经历本身不会被删除或排除</div>
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
import { profileApi, type InternshipExperienceDTO } from '@/api/profile';

const router = useRouter();

const list = ref<InternshipExperienceDTO[]>([]);
const loading = ref(false);
const saving = ref(false);
const dialogVisible = ref(false);
const editingForm = reactive<InternshipExperienceDTO>({});

async function loadData() {
  loading.value = true;
  try {
    const profile = await profileApi.getProfile();
    list.value = profile.internshipList || [];
  } finally {
    loading.value = false;
  }
}

function openDialog(row?: InternshipExperienceDTO) {
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
    await profileApi.saveInternship(editingForm);
    ElMessage.success('保存成功');
    dialogVisible.value = false;
    await loadData();
  } finally {
    saving.value = false;
  }
}

function goVariants(row: InternshipExperienceDTO) {
  router.push({ path: '/variants', query: { sourceType: 'internship', sourceId: String(row.id) } });
}

async function handleDelete(id: number) {
  await ElMessageBox.confirm('确定删除该实习经历？', '提示');
  await profileApi.deleteInternship(id);
  ElMessage.success('删除成功');
  await loadData();
}

onMounted(loadData);
</script>
