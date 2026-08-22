<template>
  <div class="login-container">
    <el-card class="login-card">
      <template #header>
        <h2>ResumeFlow 注册</h2>
      </template>
      <el-form :model="form" label-width="80px">
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="3~50 字符" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" placeholder="6~50 字符" show-password />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="选填" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" placeholder="选填" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleRegister" :loading="loading" style="width: 100%">注册</el-button>
        </el-form-item>
        <el-form-item>
          <el-link type="primary" @click="$router.push('/login')">已有账号？去登录</el-link>
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

const router = useRouter();
const loading = ref(false);

const form = reactive({
  username: '',
  password: '',
  email: '',
  phone: '',
});

async function handleRegister() {
  if (!form.username || !form.password) {
    ElMessage.warning('请填写用户名和密码');
    return;
  }
  loading.value = true;
  try {
    await authApi.register(form);
    ElMessage.success('注册成功，请登录');
    router.push('/login');
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
