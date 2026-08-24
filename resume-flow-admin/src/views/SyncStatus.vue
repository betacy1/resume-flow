<template>
  <div>
    <el-card>
      <template #header>
        <div style="display: flex; align-items: center; justify-content: space-between">
          <span>插件同步状态</span>
          <el-button type="primary" :loading="loading" @click="load">手动刷新</el-button>
        </div>
      </template>

      <el-descriptions v-if="status" :column="1" border>
        <el-descriptions-item label="数据版本号 profile_version">
          <el-tag type="primary">v{{ status.profileVersion }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="内容哈希 data_hash">
          <code>{{ status.dataHash }}</code>
        </el-descriptions-item>
        <el-descriptions-item label="最后更新时间">{{ status.updatedAt }}</el-descriptions-item>
      </el-descriptions>
      <el-empty v-else-if="!loading" description="暂无数据" />

      <el-alert
        style="margin-top: 16px"
        type="info"
        :closable="false"
        title="说明"
        description="管理端任何简历数据修改都会使版本号 +1 并重新计算内容哈希；浏览器插件在打开面板时会请求 /api/sync/status，发现版本落后或哈希不一致时自动拉取 /api/sync/full 全量更新本地缓存。"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { syncApi, type SyncStatusVO } from '@/api/sync';

const status = ref<SyncStatusVO | null>(null);
const loading = ref(false);

async function load() {
  loading.value = true;
  try {
    status.value = await syncApi.status();
  } catch (e: any) {
    ElMessage.error(e?.message || '同步状态加载失败');
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>
