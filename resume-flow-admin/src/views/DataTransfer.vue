<template>
  <div>
    <h2>数据导入导出</h2>

    <el-card style="margin-bottom: 16px">
      <template #header><b>导出</b></template>
      <p style="color: #606266; font-size: 13px; margin: 0 0 12px">
        将当前用户的全部简历配置（基础信息、教育/实习/项目经历、技能、开放题素材、自定义字段、模板配置、内容版本）导出为 JSON 文件。
      </p>
      <el-button type="primary" :loading="exporting" @click="handleExport">导出全部配置为 JSON</el-button>
    </el-card>

    <el-card>
      <template #header><b>导入</b></template>
      <p style="color: #606266; font-size: 13px; margin: 0 0 12px">
        从 JSON 文件导入。导入按 <b>fieldKey</b>（自定义字段）与 <b>类型+标题</b>（开放题素材）合并，避免重复。
      </p>

      <div style="display: flex; gap: 16px; align-items: center; margin-bottom: 16px; flex-wrap: wrap">
        <el-upload
          :auto-upload="false"
          :show-file-list="false"
          accept="application/json,.json"
          :on-change="handleFileChange"
        >
          <el-button>选择 JSON 文件</el-button>
        </el-upload>
        <el-radio-group v-model="importMode" :disabled="!preview">
          <el-radio value="merge">新增模式（仅新增，不覆盖已有）</el-radio>
          <el-radio value="overwrite">覆盖模式（同键内容覆盖为导入值）</el-radio>
        </el-radio-group>
      </div>

      <!-- 导入预览 -->
      <template v-if="preview">
        <el-descriptions :column="4" border style="margin-bottom: 12px">
          <el-descriptions-item label="文件">{{ fileName }}</el-descriptions-item>
          <el-descriptions-item label="数据版本">v{{ preview.profileVersion ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="自定义字段">{{ (preview.customFields || []).length }} 条</el-descriptions-item>
          <el-descriptions-item label="开放题素材">{{ (preview.materials || []).length }} 条</el-descriptions-item>
          <el-descriptions-item label="实习经历">{{ (preview.internshipList || []).length }} 条</el-descriptions-item>
          <el-descriptions-item label="项目经历">{{ (preview.projectList || []).length }} 条</el-descriptions-item>
          <el-descriptions-item label="教育经历">{{ (preview.educationList || []).length }} 条</el-descriptions-item>
          <el-descriptions-item label="导出时间">{{ preview.updatedAt || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-tabs>
          <el-tab-pane label="自定义字段">
            <el-table :data="preview.customFields || []" border max-height="320">
              <el-table-column prop="fieldKey" label="fieldKey" width="200" />
              <el-table-column prop="fieldName" label="字段名" width="160" />
              <el-table-column prop="fieldCategory" label="分类" width="120" />
              <el-table-column label="内容" min-width="240">
                <template #default="{ row }">{{ truncate(row.fieldValue) }}</template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="开放题素材">
            <el-table :data="preview.materials || []" border max-height="320">
              <el-table-column prop="title" label="标题" width="180" />
              <el-table-column label="类型" width="150">
                <template #default="{ row }">{{ row.materialType }}</template>
              </el-table-column>
              <el-table-column prop="wordLimitType" label="字数版本" width="110" />
              <el-table-column label="内容" min-width="240">
                <template #default="{ row }">{{ truncate(row.content) }}</template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>

        <el-button type="primary" :loading="importing" style="margin-top: 12px" @click="handleImport">
          确认导入（{{ importMode === 'overwrite' ? '覆盖模式' : '新增模式' }}）
        </el-button>
      </template>
      <el-empty v-else description="选择 JSON 文件后，将在此展示导入预览" style="padding: 24px 0" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { ElMessage } from 'element-plus';
import { dataTransferApi } from '@/api/dataTransfer';

const exporting = ref(false);
const importing = ref(false);
const fileName = ref('');
const preview = ref<Record<string, any> | null>(null);
const importMode = ref<'merge' | 'overwrite'>('merge');

function truncate(text?: string) {
  if (!text) return '';
  return text.length > 60 ? text.slice(0, 60) + '…' : text;
}

/** 导出：调用后端接口，将返回的 JSON 下载为文件 */
async function handleExport() {
  exporting.value = true;
  try {
    const data = await dataTransferApi.export();
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `resumefill-export-${new Date().toISOString().slice(0, 10)}.json`;
    a.click();
    URL.revokeObjectURL(url);
    ElMessage.success('导出成功');
  } finally {
    exporting.value = false;
  }
}

/** 选择文件后本地解析并展示预览（尚未导入） */
async function handleFileChange(file: any) {
  try {
    const text = await file.raw.text();
    const data = JSON.parse(text);
    if (!data || typeof data !== 'object' || (!data.customFields && !data.materials && !data.basicInfo)) {
      ElMessage.error('文件格式不正确：缺少 customFields / materials / basicInfo');
      return;
    }
    fileName.value = file.name;
    preview.value = data;
  } catch {
    ElMessage.error('JSON 解析失败，请检查文件内容');
  }
}

/** 确认导入：按 fieldKey / 类型+标题 合并 */
async function handleImport() {
  if (!preview.value) return;
  importing.value = true;
  try {
    const res = await dataTransferApi.import(preview.value, importMode.value);
    ElMessage.success(`导入完成：新增 ${res.added} 条，覆盖 ${res.updated} 条，跳过 ${res.skipped} 条`);
    preview.value = null;
  } finally {
    importing.value = false;
  }
}
</script>
