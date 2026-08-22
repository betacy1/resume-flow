<template>
  <div>
    <h2>Dashboard</h2>
    <el-row :gutter="20">
      <el-col :span="6">
        <el-card>
          <div class="stat-card">
            <div class="stat-label">岗位模板数</div>
            <div class="stat-value">{{ templateCount }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card>
          <div class="stat-card">
            <div class="stat-label">素材数</div>
            <div class="stat-value">{{ materialCount }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card>
          <div class="stat-card">
            <div class="stat-label">教育经历数</div>
            <div class="stat-value">{{ profile?.educationList?.length || 0 }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card>
          <div class="stat-card">
            <div class="stat-label">实习经历数</div>
            <div class="stat-value">{{ profile?.internshipList?.length || 0 }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card style="margin-top: 20px">
      <template #header>使用指南</template>
      <ol>
        <li>在「基础信息管理」中填写姓名、手机、邮箱、学校等</li>
        <li>在「教育经历」「实习经历」「项目经历」「技能信息」中维护详细数据</li>
        <li>在「岗位模板管理」中创建不同岗位版本（后端开发版、AI 应用版等）</li>
        <li>在「开放题素材管理」中维护自我评价、AI 协作经历等长文本素材</li>
        <li>安装浏览器插件，登录后选择岗位模板，在网申页面点击「扫描页面并自动填充」</li>
        <li>插件仅辅助填写，不会自动提交表单，请人工检查后提交</li>
      </ol>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { profileApi, type ProfileVO } from '@/api/profile';
import { templateApi } from '@/api/template';
import { materialApi } from '@/api/template';

const profile = ref<ProfileVO | null>(null);
const templateCount = ref(0);
const materialCount = ref(0);

onMounted(async () => {
  try {
    const [p, templates, materials] = await Promise.all([
      profileApi.getProfile(),
      templateApi.list(),
      materialApi.list(),
    ]);
    profile.value = p;
    templateCount.value = templates.length;
    materialCount.value = materials.length;
  } catch {
    // ignore
  }
});
</script>

<style scoped>
.stat-card {
  text-align: center;
  padding: 10px 0;
}
.stat-label {
  font-size: 14px;
  color: #909399;
}
.stat-value {
  font-size: 32px;
  font-weight: bold;
  color: #409eff;
  margin-top: 8px;
}
ol {
  padding-left: 20px;
  line-height: 2;
  color: #606266;
}
</style>
