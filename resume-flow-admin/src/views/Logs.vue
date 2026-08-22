<template>
  <div>
    <h2>自动填充日志</h2>
    <el-table :data="logs" v-loading="loading" border>
      <el-table-column prop="pageTitle" label="页面标题" width="180" show-overflow-tooltip />
      <el-table-column prop="pageUrl" label="页面 URL" show-overflow-tooltip />
      <el-table-column prop="totalFields" label="总字段数" width="100" />
      <el-table-column prop="matchedCount" label="匹配数" width="80" />
      <el-table-column prop="filledCount" label="填写数" width="80" />
      <el-table-column prop="skippedCount" label="跳过" width="80" />
      <el-table-column prop="sensitiveCount" label="敏感" width="80" />
      <el-table-column prop="status" label="状态" width="80" />
      <el-table-column prop="clientIp" label="IP" width="120" />
      <el-table-column prop="createTime" label="时间" width="180" />
    </el-table>
    <el-pagination
      v-model:current-page="page"
      :page-size="size"
      :total="total"
      layout="total, prev, pager, next"
      @current-change="loadData"
      style="margin-top: 16px; justify-content: flex-end"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { autofillApi } from '@/api/template';

const logs = ref<any[]>([]);
const loading = ref(false);
const page = ref(1);
const size = ref(20);
const total = ref(0);

async function loadData() {
  loading.value = true;
  try {
    const result = await autofillApi.getLogs(page.value - 1, size.value);
    logs.value = result.content || [];
    total.value = result.totalElements || 0;
  } finally {
    loading.value = false;
  }
}

onMounted(loadData);
</script>
