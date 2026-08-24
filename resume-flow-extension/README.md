# ResumeFlow 浏览器插件

## 技术栈

- TypeScript
- Vite + @crxjs/vite-plugin
- WebExtension API (Manifest V3)

## 兼容浏览器

- Chrome
- Edge
- Brave
- Firefox

## 本地开发

### 1. 环境要求

- Node.js 18+
- npm

### 2. 安装依赖

```bash
cd resume-flow-extension
npm install
```

### 3. 开发模式

```bash
npm run dev
```

### 4. 构建生产包

```bash
npm run build
```

构建后 `dist/` 目录包含插件文件和 zip 包。

## 安装插件

### Chrome / Edge / Brave

1. 打开 `chrome://extensions`（或 `edge://extensions`）
2. 开启右上角「开发者模式」
3. 点击「加载已解压的扩展程序」
4. 选择 `dist/` 目录

### Firefox

1. 打开 `about:debugging`
2. 选择「此 Firefox」→「临时加载附加组件」
3. 选择 `dist/manifest.json`

## 使用说明

1. 点击插件图标，在弹窗中输入后端地址、账号、密码，点击登录
2. 选择岗位模板（如后端开发版、AI 应用版）
3. 在网申页面点击「扫描页面并自动填充」
   - 插件扫描页面所有 input/textarea/select/contenteditable
   - 发送到后端匹配接口
   - 自动填写匹配的字段
4. 长文本一键填入：
   - 先点击网页中的文本框
   - 在插件弹窗中点击对应按钮（如「填入自我评价」「填入京东实习」）
5. 填写完成后，人工检查再提交

## 安全限制

- 插件禁止自动点击提交/确认/下一步/投递/保存并提交按钮
- 敏感字段（身份证、银行卡等）默认不自动填
- 所有填写结果必须由用户人工检查后提交

## 发布说明

### Chrome Web Store

1. 执行 `npm run build` 生成 `dist/resume-flow-extension.zip`
2. 访问 [Chrome Web Store Developer Dashboard](https://chrome.google.com/webstore/devconsole/)
3. 上传 zip 包，填写信息，提交审核

### Edge Add-ons

1. 执行 `npm run build` 生成 zip
2. 访问 [Edge Partner Center](https://partner.microsoft.com/dashboard/microsoftedge/)
3. 创建新扩展，上传 zip，提交审核

### Firefox Add-ons

1. 执行 `npm run build`
2. 访问 [Firefox Add-on Developer Hub](https://addons.mozilla.org/developers/)
3. 提交新附加组件，上传 zip 或源码
