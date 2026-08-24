<template>
  <div>
    <el-card>
      <template #header>
        <div style="display: flex; align-items: center; justify-content: space-between">
          <span>简历模板预览</span>
          <div>
            <el-select v-model="templateId" placeholder="选择模板" style="width: 240px" @change="loadPreview">
              <el-option v-for="t in templates" :key="t.id" :label="t.name" :value="t.id!" />
            </el-select>
          </div>
        </div>
      </template>

      <el-empty v-if="!preview" description="请选择模板查看完整简历预览" />

      <div v-else class="resume">
        <div class="resume-head">
          <h2>{{ preview.basicInfo?.name || '未填写姓名' }}</h2>
          <div class="meta">
            <span v-if="preview.basicInfo?.phone">{{ preview.basicInfo.phone }}</span>
            <span v-if="preview.basicInfo?.email">{{ preview.basicInfo.email }}</span>
            <span v-if="preview.basicInfo?.targetPosition">求职意向：{{ preview.basicInfo.targetPosition }}</span>
          </div>
          <el-tag size="small" type="info">模板：{{ preview.template.name }}（{{ preview.template.audienceType }}）</el-tag>
        </div>

        <section v-if="preview.educationList?.length">
          <h3>教育经历</h3>
          <div v-for="(edu, i) in preview.educationList" :key="i" class="item">
            <div class="item-head">
              <strong>{{ edu.schoolName }}</strong>
              <span>{{ edu.major }}</span>
              <span v-if="edu.degree">{{ edu.degree }}</span>
              <span class="time">{{ edu.startTime }} ~ {{ edu.endTime }}</span>
            </div>
          </div>
        </section>

        <section v-if="preview.internships?.length">
          <h3>实习经历</h3>
          <div v-for="item in preview.internships" :key="`i-${item.source.id}`" class="item">
            <div class="item-head">
              <strong>{{ item.source.companyName }}</strong>
              <span>{{ item.source.position }}</span>
              <span class="time">{{ item.source.startTime }} ~ {{ item.source.endTime }}</span>
            </div>
            <el-tag v-if="item.emphasisTags" size="small" type="warning" style="margin: 4px 0">
              侧重：{{ item.emphasisTags }}
            </el-tag>
            <p class="desc">{{ item.source.description }}</p>
          </div>
        </section>

        <section v-if="preview.projects?.length">
          <h3>项目经历</h3>
          <div v-for="item in preview.projects" :key="`p-${item.source.id}`" class="item">
            <div class="item-head">
              <strong>{{ item.source.projectName }}</strong>
              <span v-if="item.source.role">角色：{{ item.source.role }}</span>
              <span class="time">{{ item.source.startTime }} ~ {{ item.source.endTime }}</span>
            </div>
            <el-tag v-if="item.emphasisTags" size="small" type="warning" style="margin: 4px 0">
              侧重：{{ item.emphasisTags }}
            </el-tag>
            <p class="desc">{{ item.source.description }}</p>
          </div>
        </section>

        <section v-if="preview.skills?.ordered?.length">
          <h3>专业技能</h3>
          <div v-for="(sk, i) in preview.skills.ordered" :key="sk.skillKey" class="item">
            <div class="item-head">
              <strong>{{ i + 1 }}. {{ sk.title }}</strong>
            </div>
            <p class="desc">{{ sk.content }}</p>
          </div>
          <el-descriptions :column="1" border size="small" style="margin-top: 12px">
            <el-descriptions-item label="技能关键词">
              {{ preview.skills.keywords }}
              <el-tag size="small">{{ (preview.skills.keywords || '').length }} 字</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="简短版">
              {{ preview.skills.short }}
              <el-tag size="small">{{ (preview.skills.short || '').length }} 字</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="完整版">
              {{ preview.skills.full }}
              <el-tag size="small">{{ (preview.skills.full || '').length }} 字</el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </section>
        <el-alert v-else type="warning" :closable="false" title="当前模板预览缺少专业技能模块，请检查技能数据初始化" />

        <section v-if="preview.awards?.length">
          <h3>奖项荣誉</h3>
          <ul>
            <li v-for="(a, i) in preview.awards" :key="i">{{ a.awardName || a.name || a }}</li>
          </ul>
        </section>

        <section v-if="preview.selfEvaluation">
          <h3>自我评价</h3>
          <p class="desc">{{ preview.selfEvaluation }}</p>
        </section>

        <section v-if="preview.careerPlan">
          <h3>职业规划</h3>
          <p class="desc">{{ preview.careerPlan }}</p>
        </section>

        <section v-if="preview.aiCollaboration">
          <h3>AI 协作经历</h3>
          <p class="desc">{{ preview.aiCollaboration }}</p>
        </section>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { templateApi, type ApplicationTemplateDTO, type ResumePreviewVO } from '@/api/template';

const templates = ref<ApplicationTemplateDTO[]>([]);
const templateId = ref<number>();
const preview = ref<ResumePreviewVO | null>(null);

async function loadTemplates() {
  templates.value = await templateApi.list();
  if (templates.value.length && !templateId.value) {
    const def = templates.value.find((t) => t.isDefault) || templates.value[0];
    templateId.value = def.id;
    await loadPreview();
  }
}

async function loadPreview() {
  if (!templateId.value) return;
  preview.value = null;
  try {
    preview.value = await templateApi.resumePreview(templateId.value);
  } catch (e: any) {
    ElMessage.error(e?.message || '预览加载失败');
  }
}

onMounted(loadTemplates);
</script>

<style scoped>
.resume {
  max-width: 860px;
  margin: 0 auto;
  line-height: 1.7;
}
.resume-head {
  text-align: center;
  margin-bottom: 16px;
}
.resume-head h2 {
  margin: 0 0 8px;
}
.meta {
  display: flex;
  gap: 16px;
  justify-content: center;
  color: #606266;
  margin-bottom: 8px;
}
section {
  margin-bottom: 20px;
}
section h3 {
  border-left: 4px solid #409eff;
  padding-left: 8px;
  margin: 0 0 10px;
}
.item {
  margin-bottom: 10px;
}
.item-head {
  display: flex;
  gap: 12px;
  align-items: baseline;
  flex-wrap: wrap;
}
.item-head .time {
  color: #909399;
  margin-left: auto;
}
.desc {
  margin: 4px 0 0;
  color: #303133;
  white-space: pre-wrap;
}
</style>
