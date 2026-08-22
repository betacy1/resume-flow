<template>
  <div class="login-container">
    <el-card class="login-card">
      <template #header>
        <h2>ResumeFlow 登录</h2>
      </template>
      <el-form :model="form" label-width="80px">
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleLogin" :loading="loading" style="width: 100%">登录</el-button>
        </el-form-item>
        <el-form-item>
          <el-link type="primary" @click="$router.push('/register')">没有账号？去注册</el-link>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { authApi } from '@/api/auth';
import { useAuthStore } from '@/stores/auth';

const router = useRouter();
const authStore = useAuthStore();
const loading = ref(false);

const form = reactive({
  username: '',
  password: '',
});

async function handleLogin() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码');
    return;
  }
  loading.value = true;
  try {
    const result = await authApi.login({ username: form.username, password: form.password });
    authStore.setAuth(result.token, result.username);
    ElMessage.success('登录成功');
    router.push('/dashboard');
  } catch {
    // 错误已在拦截器中处理
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background: #f0f2f5;
}
.login-card {
  width: 420px;
}
.login-card h2 {
  text-align: center;
  margin: 0;
  color: #409eff;
}
</style>
