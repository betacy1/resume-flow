<template>
  <div>
    <h2>基础信息管理</h2>
    <el-card>
      <el-form :model="form" label-width="120px" v-loading="loading">
        <el-form-item label="姓名">
          <el-input v-model="form.name" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="性别">
          <el-select v-model="form.gender" placeholder="请选择性别">
            <el-option label="男" value="男" />
            <el-option label="女" value="女" />
          </el-select>
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="QQ">
          <el-input v-model="form.qq" placeholder="请输入 QQ 号" />
        </el-form-item>
        <el-form-item label="微信">
          <el-input v-model="form.wechat" placeholder="请输入微信号" />
        </el-form-item>
        <el-form-item label="当前所在地">
          <el-input v-model="form.currentLocation" placeholder="如 中国大陆 / 北京 / 北京市" />
        </el-form-item>
        <el-form-item label="政治面貌">
          <el-input v-model="form.politicalStatus" placeholder="可为空" />
        </el-form-item>
        <el-form-item label="应聘类型">
          <el-input v-model="form.applicantType" placeholder="如 应届毕业生" />
        </el-form-item>
        <el-form-item label="目标岗位">
          <el-input v-model="form.targetPosition" placeholder="如 AI应用工程师" />
        </el-form-item>
        <el-form-item label="目标城市">
          <el-input v-model="form.targetCity" placeholder="如 北京" />
        </el-form-item>
        <el-form-item label="接受其他城市">
          <el-select v-model="form.acceptOtherCity" placeholder="请选择">
            <el-option label="是" value="是" />
            <el-option label="否" value="否" />
          </el-select>
        </el-form-item>
        <el-form-item label="学校">
          <el-input v-model="form.school" placeholder="请输入学校" />
        </el-form-item>
        <el-form-item label="专业">
          <el-input v-model="form.major" placeholder="请输入专业" />
        </el-form-item>
        <el-form-item label="学历">
          <el-select v-model="form.degree" placeholder="请选择学历">
            <el-option label="大专" value="大专" />
            <el-option label="本科" value="本科" />
            <el-option label="硕士" value="硕士" />
            <el-option label="硕士研究生" value="硕士研究生" />
            <el-option label="博士" value="博士" />
          </el-select>
        </el-form-item>
        <el-form-item label="毕业时间">
          <el-input v-model="form.graduationDate" placeholder="如 2025-06" />
        </el-form-item>
        <el-form-item label="期望城市">
          <el-input v-model="form.expectedCity" placeholder="如 北京" />
        </el-form-item>
        <el-form-item label="期望岗位">
          <el-input v-model="form.expectedPosition" placeholder="如 后端开发工程师" />
        </el-form-item>
        <el-form-item label="自我评价">
          <el-input v-model="form.selfIntroduction" type="textarea" :rows="4" placeholder="请输入自我评价" />
        </el-form-item>
        <el-divider content-position="left">自动填写字段（非敏感，按需维护）</el-divider>
        <el-form-item label="身份证号">
          <el-input v-model="form.idCard" placeholder="自动填写字段" />
        </el-form-item>
        <el-form-item label="紧急联系人">
          <el-input v-model="form.emergencyContact" placeholder="自动填写字段" />
        </el-form-item>
        <el-form-item label="紧急联系人电话">
          <el-input v-model="form.emergencyPhone" placeholder="自动填写字段" />
        </el-form-item>
        <el-form-item label="证明人电话">
          <el-input v-model="form.referencePhone" placeholder="自动填写字段" />
        </el-form-item>
        <el-form-item label="银行卡号">
          <el-input v-model="form.bankCard" placeholder="自动填写字段" />
        </el-form-item>
        <el-form-item label="家庭成员">
          <el-input v-model="form.familyMembers" type="textarea" :rows="2" placeholder="自动填写字段" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { profileApi, type UserProfileDTO } from '@/api/profile';

const loading = ref(false);
const saving = ref(false);

const form = reactive<UserProfileDTO>({});

onMounted(async () => {
  loading.value = true;
  try {
    const profile = await profileApi.getProfile();
    if (profile.basicInfo) {
      Object.assign(form, profile.basicInfo);
    }
  } finally {
    loading.value = false;
  }
});

async function handleSave() {
  saving.value = true;
  try {
    await profileApi.saveProfile(form);
    ElMessage.success('保存成功');
  } finally {
    saving.value = false;
  }
}
</script>
