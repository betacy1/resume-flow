# ResumeFlow 后端服务

## 技术栈

- Java 17
- Spring Boot 3.2.5
- Spring Security + JWT
- Spring Data JPA + Hibernate
- H2 Database（dev）/ MySQL（prod）
- Knife4j (Swagger UI)

## 本地启动

### 1. 环境要求

- JDK 17+
- Maven 3.8+

### 2. 启动（dev 模式，使用 H2）

```bash
cd resume-flow-backend
mvn spring-boot:run
```

启动后：
- 后端地址：http://localhost:8080
- H2 控制台：http://localhost:8080/h2-console
  - JDBC URL: `jdbc:h2:file:./data/resume-flow`
  - 用户名: `sa`，密码: 空
- API 文档（Knife4j）：http://localhost:8080/doc.html
- Swagger UI：http://localhost:8080/swagger-ui.html

### 3. 切换 MySQL（prod 模式）

修改 `application.yml` 中的 `spring.profiles.active` 为 `prod`，并配置 MySQL 连接信息。

## 接口列表

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/auth/register | 用户注册 |
| POST | /api/auth/login | 用户登录 |
| POST | /api/auth/logout | 退出登录 |
| GET  | /api/auth/me | 获取当前用户信息 |
| PUT  | /api/auth/password | 修改密码 |
| GET  | /api/profile | 查询完整简历信息 |
| PUT  | /api/profile | 保存/更新基础信息 |
| POST | /api/profile/education | 新增/编辑教育经历 |
| DELETE | /api/profile/education/{id} | 删除教育经历 |
| POST | /api/profile/internship | 新增/编辑实习经历 |
| DELETE | /api/profile/internship/{id} | 删除实习经历 |
| POST | /api/profile/project | 新增/编辑项目经历 |
| DELETE | /api/profile/project/{id} | 删除项目经历 |
| POST | /api/profile/skill | 新增/编辑技能 |
| DELETE | /api/profile/skill/{id} | 删除技能 |
| GET  | /api/templates | 查询岗位模板列表 |
| POST | /api/templates | 新建岗位模板 |
| PUT  | /api/templates/{id} | 更新岗位模板 |
| DELETE | /api/templates/{id} | 删除岗位模板 |
| GET  | /api/materials | 查询素材列表（可按 materialType/templateId 筛选） |
| POST | /api/materials | 新建素材 |
| PUT  | /api/materials/{id} | 更新素材 |
| DELETE | /api/materials/{id} | 删除素材 |
| GET  | /api/custom-fields | 查询自定义字段（可按 keyword/category/enabled/templateId 筛选） |
| POST | /api/custom-fields | 新增字段 |
| PUT  | /api/custom-fields/{id} | 编辑字段 |
| DELETE | /api/custom-fields/{id} | 删除字段 |
| PUT  | /api/custom-fields/{id}/enabled | 启用/禁用字段 |
| POST | /api/autofill/match | 字段匹配接口 |
| GET  | /api/autofill/logs | 查询填充日志 |

## 数据库表

| 表名 | 说明 |
|------|------|
| sys_user | 系统用户 |
| user_profile | 简历基础信息 |
| education_experience | 教育经历 |
| internship_experience | 实习经历 |
| project_experience | 项目经历 |
| skill_profile | 技能信息 |
| application_template | 岗位模板 |
| user_custom_field | 自定义字段（含匹配关键词、敏感标识） |
| answer_material | 素材库 |
| autofill_log | 自动填充日志 |

## 测试

### 注册用户

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"123456"}'
```

### 登录

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"123456"}'
```

### 字段匹配

```bash
curl -X POST http://localhost:8080/api/autofill/match \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <TOKEN>" \
  -d '{"templateId":1,"pageUrl":"https://example.com","fields":[{"fieldId":"f1","label":"姓名","type":"input","nearbyText":"姓名"}]}'
```
