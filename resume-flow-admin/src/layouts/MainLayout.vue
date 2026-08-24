<template>
  <el-container style="height: 100vh">
    <el-aside width="220px" style="background: #304156">
      <div class="logo">ResumeFlow</div>
      <el-menu
        :default-active="$route.path"
        router
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409eff"
      >
        <el-menu-item index="/dashboard">
          <el-icon><HomeFilled /></el-icon>
          <span>首页 Dashboard</span>
        </el-menu-item>
        <el-menu-item index="/profile">
          <el-icon><User /></el-icon>
          <span>基础信息管理</span>
        </el-menu-item>
        <el-menu-item index="/education">
          <el-icon><Reading /></el-icon>
          <span>教育经历管理</span>
        </el-menu-item>
        <el-menu-item index="/internship">
          <el-icon><Briefcase /></el-icon>
          <span>实习经历管理</span>
        </el-menu-item>
        <el-menu-item index="/project">
          <el-icon><Files /></el-icon>
          <span>项目经历管理</span>
        </el-menu-item>
        <el-menu-item index="/skill">
          <el-icon><Star /></el-icon>
          <span>技能信息管理</span>
        </el-menu-item>
        <el-menu-item index="/awards">
          <el-icon><Trophy /></el-icon>
          <span>奖项荣誉管理</span>
        </el-menu-item>
        <el-menu-item index="/variants">
          <el-icon><Notebook /></el-icon>
          <span>内容版本管理</span>
        </el-menu-item>
        <el-menu-item index="/templates">
          <el-icon><Document /></el-icon>
          <span>岗位模板管理</span>
        </el-menu-item>
        <el-menu-item index="/resume-preview">
          <el-icon><View /></el-icon>
          <span>简历模板预览</span>
        </el-menu-item>
        <el-menu-item index="/fields">
          <el-icon><EditPen /></el-icon>
          <span>字段管理</span>
        </el-menu-item>
        <el-menu-item index="/materials">
          <el-icon><Collection /></el-icon>
          <span>开放题素材管理</span>
        </el-menu-item>
        <el-menu-item index="/logs">
          <el-icon><List /></el-icon>
          <span>自动填充日志</span>
        </el-menu-item>
        <el-menu-item index="/sync-status">
          <el-icon><Refresh /></el-icon>
          <span>同步状态</span>
        </el-menu-item>
        <el-menu-item index="/data-transfer">
          <el-icon><Download /></el-icon>
          <span>数据导入导出</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header style="display: flex; justify-content: flex-end; align-items: center; background: #fff; border-bottom: 1px solid #e6e6e6">
        <span style="margin-right: 16px; color: #606266">用户: {{ authStore.username }}</span>
        <el-button type="text" @click="handleLogout">退出登录</el-button>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { useAuthStore } from '@/stores/auth';
import { useRouter } from 'vue-router';
import { authApi } from '@/api/auth';
import {
  HomeFilled, User, Reading, Briefcase, Files, Star, Document, Collection, List, EditPen, Trophy, Notebook, View, Refresh, Download,
} from '@element-plus/icons-vue';

const authStore = useAuthStore();
const router = useRouter();

async function handleLogout() {
  try {
    await authApi.logout();
  } finally {
    authStore.clearAuth();
    router.push('/login');
  }
}
</script>

<style scoped>
.logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  color: #fff;
  font-size: 20px;
  font-weight: bold;
  background: #2b3a4f;
}
</style>
