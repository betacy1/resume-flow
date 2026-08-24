<template>
  <div class="applications-page">
    <!-- 筛选与操作区 -->
    <el-card shadow="never" class="toolbar-card">
      <el-form inline size="small" @submit.prevent>
        <el-form-item label="搜索">
          <el-input v-model="query.keyword" placeholder="公司/机构/岗位/官网/公众号/备注" clearable
            style="width: 220px" @keyup.enter="reload" @clear="reload" />
        </el-form-item>
        <el-form-item label="批次">
          <el-select v-model="query.batchName" clearable placeholder="全部" style="width: 130px" @change="reload">
            <el-option v-for="b in options.batchNames" :key="b" :label="b" :value="b" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.applyStatus" clearable placeholder="全部" style="width: 110px" @change="reload">
            <el-option v-for="s in options.applyStatuses" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="公司">
          <el-input v-model="query.companyName" clearable placeholder="公司" style="width: 120px"
            @keyup.enter="reload" @clear="reload" />
        </el-form-item>
        <el-form-item label="机构">
          <el-input v-model="query.organizationName" clearable placeholder="机构" style="width: 120px"
            @keyup.enter="reload" @clear="reload" />
        </el-form-item>
        <el-form-item label="岗位">
          <el-input v-model="query.positionName" clearable placeholder="岗位" style="width: 120px"
            @keyup.enter="reload" @clear="reload" />
        </el-form-item>
        <el-form-item label="企业性质">
          <el-select v-model="query.companyNature" clearable filterable placeholder="全部" style="width: 150px"
            @change="reload">
            <el-option v-for="n in options.companyNatures" :key="n" :label="n" :value="n" />
          </el-select>
        </el-form-item>
        <el-form-item label="渠道">
          <el-select v-model="query.applicationChannel" clearable placeholder="全部" style="width: 130px" @change="reload">
            <el-option v-for="c in options.channels" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="当前阶段">
          <el-select v-model="query.currentStage" clearable placeholder="全部" style="width: 110px" @change="reload">
            <el-option v-for="s in options.currentStages" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="插件采集">
          <el-select v-model="pluginFilter" clearable placeholder="全部" style="width: 100px" @change="reload">
            <el-option label="是" :value="1" />
            <el-option label="否" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="reload">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
      <div class="action-bar">
        <el-button type="primary" size="small" @click="openCreate">新增记录</el-button>
        <el-upload :show-file-list="false" accept=".xlsx,.xls" :http-request="handleImport">
          <el-button size="small">导入 Excel</el-button>
        </el-upload>
        <el-button size="small" :loading="exporting" @click="handleExport">导出 Excel</el-button>
        <el-select v-model="batchStatusValue" placeholder="批量修改状态" size="small" style="width: 140px">
          <el-option v-for="s in options.applyStatuses" :key="s" :label="s" :value="s" />
        </el-select>
        <el-button size="small" :disabled="!selectedIds.length || !batchStatusValue" @click="handleBatchStatus">
          应用到选中（{{ selectedIds.length }}）
        </el-button>
        <span class="total-tip">共 {{ total }} 条</span>
      </div>
    </el-card>

    <!-- 表格 -->
    <el-table :data="records" v-loading="loading" border stripe size="small" height="calc(100vh - 260px)"
      @selection-change="handleSelectionChange" @sort-change="handleSortChange">
      <el-table-column type="selection" width="40" fixed />
      <el-table-column label="状态" width="110" fixed sortable="custom" prop="applyStatus">
        <template #default="{ row }">
          <el-select :model-value="row.applyStatus" size="small" style="width: 92px"
            @change="(v: string) => handleQuickStatus(row, v)">
            <el-option v-for="s in options.applyStatuses" :key="s" :label="s" :value="s">
              <el-tag :type="statusTagType(s)" size="small" effect="plain">{{ s }}</el-tag>
            </el-option>
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="公司/单位" prop="companyName" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <span>{{ row.companyName }}</span>
          <el-tag v-if="row.priority" size="small" :type="priorityTagType(row.priority)" effect="plain"
            style="margin-left: 4px">{{ row.priority }}</el-tag>
          <el-tag v-if="row.applicationChannel === '插件采集'" size="small" type="info" effect="plain"
            style="margin-left: 4px">插件</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="机构/部门" prop="organizationName" min-width="140" show-overflow-tooltip />
      <el-table-column label="岗位" prop="positionName" min-width="140" show-overflow-tooltip />
      <el-table-column label="岗位方向" prop="positionDirection" width="90" />
      <el-table-column label="企业性质" prop="companyNature" min-width="120" show-overflow-tooltip />
      <el-table-column label="当前阶段" prop="currentStage" width="90" />
      <el-table-column label="投递渠道" prop="applicationChannel" width="100" />
      <el-table-column label="官网" width="70">
        <template #default="{ row }">
          <el-link v-if="row.officialWebsite" type="primary" :href="row.officialWebsite" target="_blank">打开</el-link>
          <span v-else class="empty-cell">-</span>
        </template>
      </el-table-column>
      <el-table-column label="公众号" prop="publicAccount" width="110" show-overflow-tooltip />
      <el-table-column label="招聘系统链接" width="100">
        <template #default="{ row }">
          <el-link v-if="row.recruitmentUrl" type="primary" :href="row.recruitmentUrl" target="_blank">打开</el-link>
          <span v-else class="empty-cell">-</span>
        </template>
      </el-table-column>
      <el-table-column label="写简历网址" width="100">
        <template #default="{ row }">
          <el-link v-if="row.resumeEditUrl" type="primary" :href="row.resumeEditUrl" target="_blank">打开</el-link>
          <span v-else class="empty-cell">-</span>
        </template>
      </el-table-column>
      <el-table-column label="简历修改时间" width="130">
        <template #default="{ row }">
          <el-tooltip v-if="row.resumeModifiedAt" :content="`来源：${row.resumeModifiedSource || '-'}`" placement="top">
            <span>{{ formatTime(row.resumeModifiedAt) }}</span>
          </el-tooltip>
          <span v-else class="empty-cell">-</span>
        </template>
      </el-table-column>
      <el-table-column label="最近访问时间" prop="lastVisitedAt" width="130" sortable="custom">
        <template #default="{ row }">{{ formatTime(row.lastVisitedAt) }}</template>
      </el-table-column>
      <el-table-column label="投递时间" prop="appliedAt" width="130" sortable="custom">
        <template #default="{ row }">{{ formatTime(row.appliedAt) }}</template>
      </el-table-column>
      <el-table-column label="截止时间" prop="deadlineAt" width="130" sortable="custom">
        <template #default="{ row }">{{ formatTime(row.deadlineAt) }}</template>
      </el-table-column>
      <el-table-column label="备注" prop="remark" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <el-tooltip v-if="row.warningNote" :content="`限制：${row.warningNote}`" placement="top">
            <span class="warning-remark">{{ row.remark || '⚠' }}</span>
          </el-tooltip>
          <span v-else>{{ row.remark }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          <el-button link type="primary" size="small" @click="openStages(row)">流程详情</el-button>
          <el-dropdown trigger="click" @command="(cmd: string) => handleCommand(cmd, row)">
            <el-button link type="primary" size="small">更多</el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="copy">复制</el-dropdown-item>
                <el-dropdown-item command="openWebsite" :disabled="!row.officialWebsite">打开官网</el-dropdown-item>
                <el-dropdown-item command="openApplication" :disabled="!row.applicationUrl">打开投递页</el-dropdown-item>
                <el-dropdown-item command="openResume" :disabled="!row.resumeEditUrl">打开写简历页</el-dropdown-item>
                <el-dropdown-item command="markApplied">标记已投</el-dropdown-item>
                <el-dropdown-item command="markNotApplied">标记未投</el-dropdown-item>
                <el-dropdown-item divided command="markFocus">标记重点关注</el-dropdown-item>
                <el-dropdown-item command="markSkip">标记暂不投</el-dropdown-item>
                <el-dropdown-item command="markUnfit">标记不合适</el-dropdown-item>
                <el-dropdown-item command="markNeedPosition">标记待补充岗位</el-dropdown-item>
                <el-dropdown-item divided command="delete">
                  <span style="color: #f56c6c">删除</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total"
      :page-sizes="[50, 100, 200]" layout="total, sizes, prev, pager, next" style="margin-top: 12px"
      @current-change="reload" @size-change="reload" />

    <!-- 新增 / 编辑弹窗 -->
    <el-dialog v-model="editVisible" :title="form.id ? '编辑投递记录' : '新增投递记录'" width="760px" destroy-on-close>
      <el-form :model="form" label-width="100px" size="small">
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="批次">
              <el-select v-model="form.batchName" filterable allow-create style="width: 100%">
                <el-option v-for="b in options.batchNames" :key="b" :label="b" :value="b" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="来源类型">
              <el-select v-model="form.sourceType" style="width: 100%">
                <el-option v-for="s in options.sourceTypes" :key="s" :label="s" :value="s" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="类别">
              <el-select v-model="form.categoryType" filterable allow-create clearable style="width: 100%">
                <el-option v-for="c in options.categoryTypes" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="公司/单位">
              <el-input v-model="form.companyName" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="机构/部门">
              <el-input v-model="form.organizationName" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="岗位">
              <el-input v-model="form.positionName" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="岗位方向">
              <el-select v-model="form.positionDirection" filterable allow-create clearable style="width: 100%">
                <el-option v-for="d in positionDirections" :key="d" :label="d" :value="d" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="企业性质">
              <el-select v-model="form.companyNature" filterable allow-create clearable style="width: 100%">
                <el-option v-for="n in options.companyNatures" :key="n" :label="n" :value="n" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="状态">
              <el-select v-model="form.applyStatus" style="width: 100%">
                <el-option v-for="s in options.applyStatuses" :key="s" :label="s" :value="s" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="当前阶段">
              <el-select v-model="form.currentStage" filterable allow-create clearable style="width: 100%">
                <el-option v-for="s in options.currentStages" :key="s" :label="s" :value="s" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="优先级">
              <el-select v-model="form.priority" clearable style="width: 100%">
                <el-option v-for="p in options.priorities" :key="p" :label="p" :value="p" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="工作城市">
              <el-input v-model="form.city" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="投递渠道">
              <el-select v-model="form.applicationChannel" filterable allow-create style="width: 100%">
                <el-option v-for="c in options.channels" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="公众号">
              <el-input v-model="form.publicAccount" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="启用">
              <el-switch v-model="form.enabled" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="官网">
              <el-input v-model="form.officialWebsite" placeholder="https://" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="招聘系统链接">
              <el-input v-model="form.recruitmentUrl" placeholder="https://" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="投递链接">
              <el-input v-model="form.applicationUrl" placeholder="https://" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="写简历网址">
              <el-input v-model="form.resumeEditUrl" placeholder="https://" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="投递时间">
              <el-date-picker v-model="form.appliedAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss"
                style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="截止时间">
              <el-date-picker v-model="form.deadlineAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss"
                style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="简历修改时间">
              <el-date-picker v-model="form.resumeModifiedAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss"
                style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="限制说明">
              <el-input v-model="form.warningNote" type="textarea" :rows="2"
                placeholder="例如：每人同时只能投递 1 个岗位" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveRecord">保存</el-button>
      </template>
    </el-dialog>

    <!-- 流程详情弹窗 -->
    <el-dialog v-model="stageVisible" :title="`流程详情：${stageRecord?.companyName || ''}`" width="680px" destroy-on-close>
      <el-table :data="stages" v-loading="stageLoading" border size="small">
        <el-table-column label="阶段" prop="stageName" width="100" />
        <el-table-column label="状态" prop="stageStatus" width="100" />
        <el-table-column label="结果" prop="stageResult" width="80" />
        <el-table-column label="时间" width="150">
          <template #default="{ row }">{{ formatTime(row.stageTime) }}</template>
        </el-table-column>
        <el-table-column label="备注" prop="note" show-overflow-tooltip />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openStageEdit(row)">编辑</el-button>
            <el-popconfirm title="确认删除该流程记录？" @confirm="deleteStage(row)">
              <template #reference>
                <el-button link type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top: 12px">
        <el-button size="small" type="primary" @click="openStageEdit()">+ 新增流程记录</el-button>
      </div>
      <el-divider />
      <el-form v-if="stageFormVisible" :model="stageForm" inline size="small">
        <el-form-item label="阶段">
          <el-select v-model="stageForm.stageName" filterable allow-create style="width: 120px">
            <el-option v-for="s in options.stageNames" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="stageForm.stageStatus" style="width: 110px">
            <el-option v-for="s in options.stageStatuses" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="结果">
          <el-select v-model="stageForm.stageResult" clearable style="width: 90px">
            <el-option v-for="s in options.stageResults" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间">
          <el-date-picker v-model="stageForm.stageTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 180px" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="stageForm.note" style="width: 160px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="saveStage">保存流程</el-button>
          <el-button @click="stageFormVisible = false">取消</el-button>
        </el-form-item>
      </el-form>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox, type UploadRequestOptions } from 'element-plus';
import {
  applicationApi,
  type ApplicationRecord,
  type ApplicationOptions,
  type ApplicationQuery,
  type ApplicationStageRecord,
} from '@/api/application';

const positionDirections = ['后端', 'AI', '金融科技', '算法', '管培', '产品', '其他'];

const loading = ref(false);
const saving = ref(false);
const exporting = ref(false);
const records = ref<ApplicationRecord[]>([]);
const total = ref(0);
const selectedIds = ref<number[]>([]);
const batchStatusValue = ref('');
const pluginFilter = ref<number | ''>('');

const defaultQuery = (): ApplicationQuery => ({
  page: 1,
  size: 50,
  keyword: '',
  batchName: '',
  applyStatus: '',
  companyName: '',
  organizationName: '',
  positionName: '',
  companyNature: '',
  applicationChannel: '',
  currentStage: '',
});
const query = reactive<ApplicationQuery>(defaultQuery());

const options = ref<ApplicationOptions>({
  applyStatuses: [], sourceTypes: [], channels: [], currentStages: [], priorities: [],
  batchNames: [], companyNatures: [], categoryTypes: [], stageNames: [], stageStatuses: [], stageResults: [],
});

// ==================== 查询 ====================

async function reload() {
  loading.value = true;
  try {
    const params: ApplicationQuery = { ...query };
    if (pluginFilter.value !== '') {
      params.pluginCollected = pluginFilter.value === 1;
    }
    const data = await applicationApi.list(params);
    records.value = data.records;
    total.value = data.total;
  } finally {
    loading.value = false;
  }
}

function resetQuery() {
  Object.assign(query, defaultQuery());
  pluginFilter.value = '';
  reload();
}

function handleSelectionChange(rows: ApplicationRecord[]) {
  selectedIds.value = rows.map((r) => r.id!).filter(Boolean);
}

function handleSortChange({ prop, order }: { prop: string; order: string | null }) {
  query.sortBy = order ? prop : undefined;
  query.sortDir = order === 'descending' ? 'desc' : 'asc';
  reload();
}

// ==================== 新增 / 编辑 ====================

const editVisible = ref(false);
const form = ref<ApplicationRecord>({});

function openCreate() {
  form.value = { batchName: '2027秋招', applyStatus: '未投', sourceType: '企业', applicationChannel: '手动添加', enabled: true };
  editVisible.value = true;
}

function openEdit(row: ApplicationRecord) {
  form.value = { ...row };
  editVisible.value = true;
}

async function saveRecord() {
  if (!form.value.companyName?.trim()) {
    ElMessage.warning('请填写公司/单位名称');
    return;
  }
  saving.value = true;
  try {
    if (form.value.id) {
      await applicationApi.update(form.value.id, form.value);
      ElMessage.success('已保存');
    } else {
      await applicationApi.create(form.value);
      ElMessage.success('已新增');
    }
    editVisible.value = false;
    reload();
  } finally {
    saving.value = false;
  }
}

// ==================== 状态 / 标记 / 删除 / 复制 ====================

async function handleQuickStatus(row: ApplicationRecord, status: string) {
  await applicationApi.updateStatus(row.id!, status);
  row.applyStatus = status;
  ElMessage.success(`${row.companyName} 状态已改为「${status}」`);
}

async function handleBatchStatus() {
  await applicationApi.batchStatus(selectedIds.value, batchStatusValue.value);
  ElMessage.success(`已将 ${selectedIds.value.length} 条记录状态改为「${batchStatusValue.value}」`);
  batchStatusValue.value = '';
  reload();
}

async function handleCommand(cmd: string, row: ApplicationRecord) {
  switch (cmd) {
    case 'copy': {
      await applicationApi.copy(row.id!);
      ElMessage.success('已复制记录');
      reload();
      break;
    }
    case 'delete': {
      await ElMessageBox.confirm(`确认删除「${row.companyName}」的投递记录？`, '删除确认', { type: 'warning' });
      await applicationApi.remove(row.id!);
      ElMessage.success('已删除');
      reload();
      break;
    }
    case 'openWebsite':
      window.open(row.officialWebsite!, '_blank');
      break;
    case 'openApplication':
      window.open(row.applicationUrl!, '_blank');
      break;
    case 'openResume':
      window.open(row.resumeEditUrl!, '_blank');
      break;
    case 'markApplied':
      await handleQuickStatus(row, '已投');
      break;
    case 'markNotApplied':
      await handleQuickStatus(row, '未投');
      break;
    case 'markFocus':
    case 'markSkip':
    case 'markUnfit':
    case 'markNeedPosition': {
      const priorityMap: Record<string, string> = {
        markFocus: '重点关注', markSkip: '暂不投', markUnfit: '不合适', markNeedPosition: '待补充岗位',
      };
      const updated = await applicationApi.update(row.id!, { ...row, priority: priorityMap[cmd] });
      Object.assign(row, updated);
      ElMessage.success(`已标记「${priorityMap[cmd]}」`);
      break;
    }
  }
}

// ==================== 导入 / 导出 ====================

async function handleImport(opt: UploadRequestOptions) {
  try {
    const result = await applicationApi.importExcel(opt.file as File);
    ElMessage.success(`导入完成：新增 ${result.created} 条，更新 ${result.updated} 条，跳过 ${result.skipped} 条`);
    reload();
  } catch (e: any) {
    ElMessage.error(e?.message || '导入失败');
  }
}

async function handleExport() {
  exporting.value = true;
  try {
    await applicationApi.exportExcel();
    ElMessage.success('导出成功');
  } catch (e: any) {
    ElMessage.error(e?.message || '导出失败');
  } finally {
    exporting.value = false;
  }
}

// ==================== 流程详情 ====================

const stageVisible = ref(false);
const stageLoading = ref(false);
const stageRecord = ref<ApplicationRecord | null>(null);
const stages = ref<ApplicationStageRecord[]>([]);
const stageFormVisible = ref(false);
const stageForm = ref<ApplicationStageRecord>({});

async function openStages(row: ApplicationRecord) {
  stageRecord.value = row;
  stageVisible.value = true;
  stageFormVisible.value = false;
  await loadStages(row.id!);
}

async function loadStages(recordId: number) {
  stageLoading.value = true;
  try {
    stages.value = await applicationApi.listStages(recordId);
  } finally {
    stageLoading.value = false;
  }
}

function openStageEdit(row?: ApplicationStageRecord) {
  stageForm.value = row ? { ...row } : { stageName: '笔试', stageStatus: '待开始' };
  stageFormVisible.value = true;
}

async function saveStage() {
  const recordId = stageRecord.value!.id!;
  if (stageForm.value.id) {
    await applicationApi.updateStage(stageForm.value.id, stageForm.value);
  } else {
    await applicationApi.createStage(recordId, stageForm.value);
  }
  ElMessage.success('流程记录已保存');
  stageFormVisible.value = false;
  await loadStages(recordId);
  reload();
}

async function deleteStage(row: ApplicationStageRecord) {
  await applicationApi.deleteStage(row.id!);
  ElMessage.success('已删除');
  await loadStages(stageRecord.value!.id!);
}

// ==================== 展示工具 ====================

function formatTime(t?: string): string {
  if (!t) return '-';
  return t.replace('T', ' ').slice(0, 16);
}

function statusTagType(status?: string) {
  if (!status) return 'info';
  if (['已投', '一面', '二面', '三面', 'HR面', '终面'].includes(status)) return 'primary';
  if (status === 'offer') return 'success';
  if (['简历挂', '已拒', '已放弃', '已截止'].includes(status)) return 'danger';
  if (['测评', '笔试'].includes(status)) return 'warning';
  return 'info';
}

function priorityTagType(priority: string) {
  if (priority === '重点关注') return 'danger';
  if (priority === '暂不投' || priority === '不合适') return 'info';
  if (priority === '待补充岗位') return 'warning';
  return 'primary';
}

onMounted(async () => {
  options.value = await applicationApi.options();
  await reload();
});
</script>

<style scoped>
.applications-page {
  padding: 4px;
}
.toolbar-card {
  margin-bottom: 12px;
}
.toolbar-card :deep(.el-form-item) {
  margin-bottom: 8px;
}
.action-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.total-tip {
  margin-left: auto;
  color: #909399;
  font-size: 12px;
}
.empty-cell {
  color: #c0c4cc;
}
.warning-remark {
  border-bottom: 1px dashed #e6a23c;
  cursor: help;
}
</style>
