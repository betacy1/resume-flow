# ResumeFlow 部署指南

## 一、Docker Compose 部署（推荐）

### 1. 环境要求

- Docker 20+
- Docker Compose 2+

### 2. 构建管理后台

```bash
cd resume-flow-admin
npm install
npm run build
cd ..
```

### 3. 启动所有服务

```bash
docker-compose up -d
```

启动后：
- 管理后台：http://your-server-ip
- 后端 API：http://your-server-ip:8080
- MySQL：端口 3306
- Redis：端口 6379

### 4. 配置 HTTPS（可选）

1. 将 SSL 证书放到 `nginx/certs/` 目录（`server.crt` 和 `server.key`）
2. 编辑 `nginx/nginx.conf`，取消 HTTPS server 配置的注释
3. 重启 Nginx 容器：`docker-compose restart admin`

## 二、阿里云 ECS 手动部署

### 1. 安装 Docker

```bash
# 安装 Docker
curl -fsSL https://get.docker.com | sh
systemctl start docker
systemctl enable docker

# 安装 Docker Compose
curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
chmod +x /usr/local/bin/docker-compose
```

### 2. 部署项目

```bash
git clone <your-repo> /opt/resumeflow
cd /opt/resumeflow

# 构建前端
cd resume-flow-admin
npm install && npm run build
cd ..

# 启动
docker-compose up -d
```

### 3. 配置防火墙

- 开放 80、443 端口（HTTP/HTTPS）
- 可选开放 8080（后端直接访问）

```bash
firewall-cmd --permanent --add-port=80/tcp
firewall-cmd --permanent --add-port=443/tcp
firewall-cmd --reload
```

### 4. 配置域名（可选）

1. 在阿里云 DNS 解析中添加 A 记录指向 ECS IP
2. 配置 Nginx HTTPS 证书

## 三、单独部署后端

```bash
cd resume-flow-backend
mvn clean package -DskipTests
java -jar target/resume-flow-backend-1.0.0.jar --spring.profiles.active=prod
```

## 四、单独部署管理后台

```bash
cd resume-flow-admin
npm run build
# 将 dist/ 目录部署到 Nginx
```

Nginx 配置参考 `nginx/nginx.conf`。

## 五、数据库切换

### dev 模式（H2）

默认使用 H2 文件数据库，无需额外配置。

### prod 模式（MySQL）

1. 修改 `application.yml`：`spring.profiles.active: prod`
2. 配置环境变量：
   ```
   MYSQL_HOST=localhost
   MYSQL_PORT=3306
   MYSQL_DATABASE=resume_flow
   MYSQL_USERNAME=resumeflow
   MYSQL_PASSWORD=your_password
   ```
3. JPA 会自动建表（ddl-auto: update）

## 六、插件与公网部署对接（阿里云 ECS）

> 插件与后端/管理后台是两套独立程序：后端 + Vue 管理后台部署在 ECS（Nginx 80 端口，`/api` 反代到后端 8081）；插件运行在用户本地浏览器。
>
> ECS 公网 IP：`123.57.70.7`（已写入插件默认后端地址，可在插件选项页修改）

### 1. 插件本地安装（开发阶段，无需上架商店）

1. 构建：`cd resume-flow-extension && npm run build`，产物为 `dist/` 目录（根目录含 `manifest.json`，另附 `resume-flow-extension.zip`）
2. Chrome 打开 `chrome://extensions/`，右上角开启【开发者模式】
3. 点击【加载已解压的扩展程序】，选择 `resume-flow-extension/dist` 目录
4. 代码修改后重新 build，再在扩展管理页点插件卡片上的刷新图标即可，不需要动 ECS

### 2. 插件访问后端的关键配置（两个坑已处理）

1. **CORS / host_permissions**：`manifest.json` 已声明 `host_permissions: ["<all_urls>"]`，且所有 API 请求经 background（`API_PROXY` 消息）从扩展上下文发出，不受网页 CORS / 混合内容限制；后端 `cors-allowed-origins` 已包含 `chrome-extension://*` 与 `http://123.57.70.7`。
2. **鉴权**：插件登录 `/api/auth/login` 拿到 JWT 后，后续所有请求自动携带 `Authorization: Bearer <token>`，与管理后台同一套鉴权。
3. 插件默认 API Base URL 为 `http://123.57.70.7`（Nginx 80 → `/api` → 8081），不依赖 localhost；本地调试可在插件选项页改回 `http://localhost:8081`。

### 3. ECS 后端更新流程（新增接口后必做）

插件新功能依赖 `/api/sync/*`、`/api/skills`、`/api/templates/{id}/resume-preview` 等接口，ECS 上后端更新前这些接口不可用：

```bash
# 在 ECS 项目目录（沿用现有流程，如 ./update.sh）
cd resume-flow-backend && mvn clean package -DskipTests
# docker 方式：docker-compose up -d --build backend
# 或 jar 方式：java -jar target/resume-flow-backend-1.0.0.jar --spring.profiles.active=prod
```

首次部署需要初始化/重建 demo 专业技能等数据时，追加环境变量 `DEMO_INIT_ENABLED=true` 重启一次（prod 默认不重建，避免覆盖线上已维护数据；初始化完成后可去掉）。

### 4. 迭代分工速查

| 修改内容 | 操作 | 是否动 ECS |
| --- | --- | --- |
| 插件代码 | 本地 build + chrome://extensions 刷新插件 | 否 |
| Vue 管理后台 | `npm run build` + 前端部署流程 | 是 |
| SpringBoot 接口 | `mvn package` + 后端部署流程（./update.sh） | 是 |

