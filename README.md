# ResumeFlow 网申简历智能填表助手

> 面向求职者的网申简历智能填表工具。用户可以在 Web 管理后台维护简历信息，安装浏览器插件后在招聘网站网申页面一键辅助填写表单。

## 项目结构

```
resumefill/
├── resume-flow-backend/       Spring Boot 后端服务
├── resume-flow-admin/         Vue3 管理后台
├── resume-flow-extension/     跨浏览器插件端
├── nginx/                      Nginx 反向代理配置
├── docker-compose.yml          Docker Compose 部署文件
└── README.md
```

## 快速开始

### 1. 启动后端（H2 文件模式，无需 MySQL）

```bash
cd resume-flow-backend
mvn spring-boot:run
```

- 后端地址：http://localhost:8080
- H2 控制台：http://localhost:8080/h2-console （JDBC URL：`jdbc:h2:file:./data/resume-flow`，用户名 sa，密码空）
- API 文档：http://localhost:8080/doc.html
- 默认账号：demo / 123456。dev 环境启动时由 `DemoDataInitializer`（CommandLineRunner）自动初始化完整简历数据：基础信息、2 段教育经历、3 条实习、6 个项目、4 项奖项荣誉、技能信息、4 个岗位模板、6 条开放题素材、44 个匹配字段，以及每条经历/项目/素材的 4 受众 × 4 长度共 240 个内容版本；已初始化则不重复插入。

### 2. 启动管理后台

```bash
cd resume-flow-admin
npm install
npm run dev
```

- 管理后台地址：http://localhost:5173

### 3. 安装浏览器插件

```bash
cd resume-flow-extension
npm install
npm run build
```

构建后在 `dist/` 目录加载插件：
- Chrome/Edge/Brave：打开 `chrome://extensions`，开启开发者模式，点击「加载已解压的扩展程序」，选择 `dist/` 目录
- Firefox：打开 `about:debugging`，选择「此 Firefox」→「临时扩展」，加载 `dist/manifest.json`

## 功能概览

- 用户注册/登录，JWT 认证
- 简历基础信息管理（姓名、性别、手机、邮箱、QQ、微信、所在地、应聘信息等），身份证号/紧急联系人/银行卡/家庭成员/证明人电话等默认作为**可自动填写字段**（非敏感），可在配置中调整
- 教育经历（含学校标签/学院/GPA/排名/导师/实验室/研究方向/论文/荣誉）、实习经历（含部门/技术栈/亮点）、项目经历（简介/职责/成果）、技能信息、**奖项荣誉**管理（`/awards`）
- **内容版本管理**（`/variants`）：每条实习/项目/素材维护大厂版/国央企版/银行版/通用版 × 200/300/500/1000 字以内共 16 个版本，后台可逐条编辑；插件按当前模板与页面字数限制自动选择最合适版本（无精确命中时自动回退到同受众更短/更长档位或通用版）
- **字段管理**：自定义字段（名称、fieldKey、类型、分类、内容、匹配关键词、敏感标识、启用/禁用、排序），支持一字段多关键词，支持搜索与分类筛选（`/fields`）
- **素材库管理**：长文本素材按类型（自我评价/实习经历/项目经历/AI协作经历/职业规划/兴趣特长/为什么选择本公司/为什么选择本岗位/补充信息）、岗位模板、字数版本管理（`/materials`）
- **岗位模板**：大厂互联网版（big_tech）、国央企版（state_owned）、银行金融科技版（bank）、通用后端开发版（general_backend），模板的受众风格决定内容版本选择（`/templates`）
- **浏览器插件**：扫描网申页面字段 → 后端智能匹配 → 一键填写；支持选择模板、快速编辑字段内容、快速新增素材、长文本一键填入当前输入框、中置信度字段人工确认填入、未匹配字段手动绑定到简历字段形成新匹配规则（`popup`）
- 自动填充日志查询（`/logs`）
- 安全限制：不自动提交表单、数据按 user_id 隔离

## 一键填写流程与匹配策略

插件点击「一键填写当前页面」后：
1. content script 扫描页面所有 `input / textarea / select / contenteditable / 富文本编辑器（Quill、wangEditor、ProseMirror、Ant Design、Element Plus 表单等）`，提取 label、placeholder、name、id、className、aria-label、父级文本、附近问题文本、maxlength 与「N字以内」字数要求（wordLimit）、可见性、禁用状态；
2. 字段信息发送到后端 `POST /api/autofill/match`（携带模板受众风格 audienceType），后端按优先级匹配：精确关键词 → 字段别名 → 字段类型 → 岗位模板 → 模糊相似度，返回 confidence 评分；
3. 三档置信度处理：
   - `confidence >= 0.75`：自动填入（含敏感标识字段，默认配置下身份证、紧急联系人等也会自动填）；
   - `0.5 <= confidence < 0.75`：不自动填，在插件报告中标为「建议人工确认」，可逐项点击确认填入；
   - `confidence < 0.5`：跳过；
4. 日期字段由 `DateFormatService` 处理：经历/项目只存标准日期（yyyy-MM-dd），按页面控件类型与占位符动态格式化为 16 种格式（`2026-05-08` / `2026.5.8` / `2026年5月8日` / `2026/05` 等）与 6 种日期范围格式；`input[type=date]` 用 yyyy-MM-dd，`input[type=month]` 用 yyyy-MM，年/月拆分字段分别填 yyyy 与 M；
5. 长文本开放题按 `字段 + 模板受众 + 字数限制` 自动选择内容版本（如「请描述与AI协作的项目，500字以内」+ 大厂版 → `ai_collaboration / big_tech / within_500`）；
6. 未匹配字段在插件中展示页面标签，可选择简历字段绑定，新增为匹配关键词（`POST /api/custom-fields/{id}/keywords`）；
7. 填写使用原生 value setter 并触发 `input / change / blur` 事件（兼容 React/Vue 受控组件），**不会自动点击提交/下一步/确认投递按钮**；
8. 每次匹配结果写入 `autofill_log`，可在后台「自动填充日志」查看；插件 options 页可查看最近一次填写报告。

返回结果字段：`fieldId / matchedFieldKey / matchedFieldName / value / confidence / sensitive / reason / variantDesc`。

## 核心数据表（H2 / MySQL 兼容）

| 表 | 说明 |
|----|------|
| `user_profile` / `education_experience` / `internship_experience` / `project_experience` / `skill_profile` / `award_certificate` | 简历各模块数据，全部带 user_id 隔离 |
| `content_variant` | 内容版本：source_type（internship/project/material）、source_id、audience_type（big_tech/state_owned/bank/general）、length_type（within_200/300/500/1000）、content、enabled |
| `application_template` | 岗位模板：含 audience_type（受众风格）与 description |
| `user_custom_field` | 自定义字段：field_key、field_name、field_type、field_category、field_value、match_keywords（JSON 字符串存 TEXT）、source_ref（关联经历/素材来源）、sensitive、enabled、sort_order |
| `answer_material` | 素材库：title、material_type、content、word_limit_type、template_id、enabled |
| `autofill_log` | 填充日志：page_url、page_title、total_fields、matched_count、filled_count、skipped_count、sensitive_count、detail_json |

建表由 JPA `ddl-auto: update` 自动完成。demo 默认数据由 `DemoDataInitializer`（仅 dev profile）在启动时初始化；字段表结构不依赖 MySQL 独有 JSON 类型（match_keywords 用 TEXT 存 JSON 字符串，content 用 TEXT）。敏感字段策略由 `application.yml` 的 `resumeflow.auto-fill-sensitive` 控制（默认 true，即敏感字段也自动填写；设为 false 则仅提示）。

## 技术栈

| 模块 | 技术栈 |
|------|--------|
| 后端 | Java 17, Spring Boot 3, Spring Security, JWT, JPA/Hibernate, H2/MySQL, Knife4j |
| 管理后台 | Vue 3, TypeScript, Vite, Pinia, Vue Router, Axios, Element Plus |
| 浏览器插件 | TypeScript, Vite, WebExtension API, @crxjs/vite-plugin |
| 部署 | Docker, Docker Compose, Nginx |

## 部署

详见 [部署指南](./DEPLOY.md)
