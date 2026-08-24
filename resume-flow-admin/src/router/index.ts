import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '@/stores/auth';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/Login.vue'),
    },
    {
      path: '/register',
      name: 'Register',
      component: () => import('@/views/Register.vue'),
    },
    {
      path: '/',
      component: () => import('@/layouts/MainLayout.vue'),
      redirect: '/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'Dashboard',
          component: () => import('@/views/Dashboard.vue'),
          meta: { title: '首页' },
        },
        {
          path: 'profile',
          name: 'Profile',
          component: () => import('@/views/BasicInfo.vue'),
          meta: { title: '基础信息' },
        },
        {
          path: 'education',
          name: 'Education',
          component: () => import('@/views/Education.vue'),
          meta: { title: '教育经历' },
        },
        {
          path: 'internship',
          name: 'Internship',
          component: () => import('@/views/Internship.vue'),
          meta: { title: '实习经历' },
        },
        {
          path: 'project',
          name: 'Project',
          component: () => import('@/views/Project.vue'),
          meta: { title: '项目经历' },
        },
        {
          path: 'skill',
          name: 'Skill',
          component: () => import('@/views/Skill.vue'),
          meta: { title: '技能信息' },
        },
        {
          path: 'awards',
          name: 'Awards',
          component: () => import('@/views/Awards.vue'),
          meta: { title: '奖项荣誉' },
        },
        {
          path: 'family',
          name: 'Family',
          component: () => import('@/views/Family.vue'),
          meta: { title: '家庭成员' },
        },
        {
          path: 'variants',
          name: 'Variants',
          component: () => import('@/views/Variants.vue'),
          meta: { title: '内容版本' },
        },
        {
          path: 'fields',
          name: 'Fields',
          component: () => import('@/views/Fields.vue'),
          meta: { title: '字段管理' },
        },
        {
          path: 'templates',
          name: 'Templates',
          component: () => import('@/views/Templates.vue'),
          meta: { title: '岗位模板' },
        },
        {
          path: 'resume-preview',
          name: 'ResumePreview',
          component: () => import('@/views/ResumePreview.vue'),
          meta: { title: '简历模板预览' },
        },
        {
          path: 'sync-status',
          name: 'SyncStatus',
          component: () => import('@/views/SyncStatus.vue'),
          meta: { title: '同步状态' },
        },
        {
          path: 'materials',
          name: 'Materials',
          component: () => import('@/views/Materials.vue'),
          meta: { title: '开放题素材' },
        },
        {
          path: 'logs',
          name: 'Logs',
          component: () => import('@/views/Logs.vue'),
          meta: { title: '填充日志' },
        },
        {
          path: 'data-transfer',
          name: 'DataTransfer',
          component: () => import('@/views/DataTransfer.vue'),
          meta: { title: '数据导入导出' },
        },
      ],
    },
  ],
});

// 路由守卫：未登录跳转登录
router.beforeEach((to, _from, next) => {
  const authStore = useAuthStore();
  if (to.path === '/login' || to.path === '/register') {
    next();
  } else if (!authStore.isLoggedIn()) {
    next('/login');
  } else {
    next();
  }
});

export default router;
