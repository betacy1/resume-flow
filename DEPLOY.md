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
