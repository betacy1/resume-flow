import axios from 'axios';
import { ElMessage } from 'element-plus';
import { useAuthStore } from '@/stores/auth';
import router from '@/router';

const request = axios.create({
  baseURL: '/api',
  timeout: 10000,
});

// 请求拦截器：自动携带 JWT Token
request.interceptors.request.use((config) => {
  const authStore = useAuthStore();
  if (authStore.token) {
    config.headers.Authorization = `Bearer ${authStore.token}`;
  }
  return config;
});

// 响应拦截器：统一处理错误
request.interceptors.response.use(
  (response) => {
    const data = response.data;
    if (data.code === 200) {
      return data.data;
    } else {
      ElMessage.error(data.message || '请求失败');
      return Promise.reject(new Error(data.message));
    }
  },
  (error) => {
    if (error.response?.status === 401) {
      const authStore = useAuthStore();
      authStore.clearAuth();
      router.push('/login');
      ElMessage.error('登录已过期，请重新登录');
    } else {
      ElMessage.error(error.message || '网络错误');
    }
    return Promise.reject(error);
  },
);

export default request;
