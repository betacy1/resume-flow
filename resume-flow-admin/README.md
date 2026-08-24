# ResumeFlow 管理后台

## 技术栈

- Vue 3 + TypeScript
- Vite
- Pinia
- Vue Router
- Axios
- Element Plus

## 本地启动

### 1. 环境要求

- Node.js 18+
- npm 或 pnpm

### 2. 安装依赖

```bash
cd resume-flow-admin
npm install
```

### 3. 启动开发服务器

```bash
npm run dev
```

- 管理后台地址：http://localhost:5173
- 已配置 Vite proxy，`/api` 请求代理到 `http://localhost:8080`

### 4. 构建生产包

```bash
npm run build
```

构建产物在 `dist/` 目录，可由 Nginx 托管。

## 页面列表

| 路径 | 页面 |
|------|------|
| /login | 登录页 |
| /register | 注册页 |
| /dashboard | 首页 Dashboard |
| /profile | 基础信息管理 |
| /education | 教育经历管理 |
| /internship | 实习经历管理 |
| /project | 项目经历管理 |
| /skill | 技能信息管理 |
| /templates | 岗位模板管理 |
| /materials | 开放题素材管理 |
| /logs | 自动填充日志 |

## 功能说明

- 登录后才能访问后台（路由守卫）
- Axios 自动携带 JWT Token
- 401 自动跳转登录页
- 支持增删改查
- 支持保存多个岗位版本
- 支持将素材绑定到不同岗位模板
