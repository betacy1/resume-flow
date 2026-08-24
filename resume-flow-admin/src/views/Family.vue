<template>
  <div>
    <h2>家庭成员与紧急联系人</h2>
    <el-tabs v-model="activeTab">
      <el-tab-pane label="家庭成员" name="family">
        <el-button type="primary" @click="openFamilyDialog()" style="margin-bottom: 16px">添加家庭成员</el-button>
        <el-table :data="familyList" v-loading="loading" border>
          <el-table-column prop="relation" label="关系" width="90" />
          <el-table-column prop="name" label="姓名" width="110">
            <template #default="{ row }">
              {{ row.name || '未填写' }}
            </template>
          </el-table-column>
          <el-table-column prop="company" label="单位">
            <template #default="{ row }">
              {{ row.company || '未填写' }}
            </template>
          </el-table-column>
          <el-table-column prop="position" label="职务" width="110">
            <template #default="{ row }">
              {{ row.position || '未填写' }}
            </template>
          </el-table-column>
          <el-table-column prop="phone" label="联系电话" width="140">
            <template #default="{ row }">
              {{ row.phone || '未填写' }}
            </template>
          </el-table-column>
          <el-table-column prop="sortOrder" label="排序" width="70" />
          <el-table-column label="操作" width="200">
            <template #default="{ row }">
              <el-button size="small" @click="openFamilyDialog(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="handleDeleteFamily(row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="紧急联系人" name="emergency">
        <el-button type="primary" @click="openEmergencyDialog()" style="margin-bottom: 16px">添加紧急联系人</el-button>
        <el-table :data="emergencyList" v-loading="loading" border>
          <el-table-column prop="name" label="姓名" width="110">
            <template #default="{ row }">
              {{ row.name || '未填写' }}
            </template>
          </el-table-column>
          <el-table-column prop="relation" label="与本人关系" width="110" />
          <el-table-column prop="phone" label="电话" width="140">
            <template #default="{ row }">
              {{ row.phone || '未填写' }}
            </template>
          </el-table-column>
          <el-table-column prop="company" label="单位" />
          <el-table-column prop="position" label="职务" width="110">
            <template #default="{ row }">
              {{ row.position || '未填写' }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200">
            <template #default="{ row }">
              <el-button size="small" @click="openEmergencyDialog(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="handleDeleteEmergency(row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="familyDialogVisible" :title="familyForm.id ? '编辑家庭成员' : '添加家庭成员'" width="560px">
      <el-form :model="familyForm" label-width="90px">
        <el-form-item label="关系">
          <el-select v-model="familyForm.relation" allow-create filterable placeholder="如 父亲 / 母亲" style="width: 100%">
            <el-option label="父亲" value="父亲" />
            <el-option label="母亲" value="母亲" />
            <el-option label="配偶" value="配偶" />
            <el-option label="兄弟" value="兄弟" />
            <el-option label="姐妹" value="姐妹" />
          </el-select>
        </el-form-item>
        <el-form-item label="姓名"><el-input v-model="familyForm.name" /></el-form-item>
        <el-form-item label="单位"><el-input v-model="familyForm.company" /></el-form-item>
        <el-form-item label="职务"><el-input v-model="familyForm.position" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="familyForm.phone" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="familyForm.email" /></el-form-item>
        <el-form-item label="政治面貌"><el-input v-model="familyForm.politicalStatus" /></el-form-item>
        <el-form-item label="地址"><el-input v-model="familyForm.address" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="familyForm.sortOrder" :min="0" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="familyForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="familyDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveFamily" :loading="saving">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="emergencyDialogVisible" :title="emergencyForm.id ? '编辑紧急联系人' : '添加紧急联系人'" width="560px">
      <el-form :model="emergencyForm" label-width="90px">
        <el-form-item label="姓名"><el-input v-model="emergencyForm.name" /></el-form-item>
        <el-form-item label="与本人关系"><el-input v-model="emergencyForm.relation" placeholder="如 母亲" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="emergencyForm.phone" /></el-form-item>
        <el-form-item label="单位"><el-input v-model="emergencyForm.company" /></el-form-item>
        <el-form-item label="职务"><el-input v-model="emergencyForm.position" /></el-form-item>
        <el-form-item label="地址"><el-input v-model="emergencyForm.address" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="emergencyForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="emergencyDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveEmergency" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { profileApi, type FamilyMemberDTO, type EmergencyContactDTO } from '@/api/profile';

const activeTab = ref('family');
const familyList = ref<FamilyMemberDTO[]>([]);
const emergencyList = ref<EmergencyContactDTO[]>([]);
const loading = ref(false);
const saving = ref(false);

const familyDialogVisible = ref(false);
const familyForm = reactive<FamilyMemberDTO>({});
const emergencyDialogVisible = ref(false);
const emergencyForm = reactive<EmergencyContactDTO>({});

async function loadData() {
  loading.value = true;
  try {
    const profile = await profileApi.getProfile();
    familyList.value = profile.familyList || [];
    emergencyList.value = profile.emergencyContactList || [];
  } finally {
    loading.value = false;
  }
}

function openFamilyDialog(row?: FamilyMemberDTO) {
  Object.keys(familyForm).forEach((k) => delete (familyForm as any)[k]);
  if (row) {
    Object.assign(familyForm, row);
  } else {
    familyForm.sortOrder = familyList.value.length;
    familyForm.enabled = true;
  }
  familyDialogVisible.value = true;
}

function openEmergencyDialog(row?: EmergencyContactDTO) {
  Object.keys(emergencyForm).forEach((k) => delete (emergencyForm as any)[k]);
  if (row) {
    Object.assign(emergencyForm, row);
  } else {
    emergencyForm.enabled = true;
  }
  emergencyDialogVisible.value = true;
}

async function handleSaveFamily() {
  saving.value = true;
  try {
    await profileApi.saveFamily(familyForm);
    ElMessage.success('保存成功');
    familyDialogVisible.value = false;
    await loadData();
  } finally {
    saving.value = false;
  }
}

async function handleSaveEmergency() {
  saving.value = true;
  try {
    await profileApi.saveEmergencyContact(emergencyForm);
    ElMessage.success('保存成功');
    emergencyDialogVisible.value = false;
    await loadData();
  } finally {
    saving.value = false;
  }
}

async function handleDeleteFamily(id: number) {
  await ElMessageBox.confirm('确定删除该家庭成员？', '提示');
  await profileApi.deleteFamily(id);
  ElMessage.success('删除成功');
  await loadData();
}

async function handleDeleteEmergency(id: number) {
  await ElMessageBox.confirm('确定删除该紧急联系人？', '提示');
  await profileApi.deleteEmergencyContact(id);
  ElMessage.success('删除成功');
  await loadData();
}

onMounted(loadData);
</script>
