<template>
  <div class="ai-analysis">
    <h3>AI 分析</h3>

    <!--
      此处原有「重新分析项目 / 推荐技术栈 / 生成里程碑」三个按钮，分别调用
      /v1/ai/analyze-project、/v1/ai/recommend-tech-stack、/v1/ai/generate-milestones。
      这三个接口后端都不存在，点击必定 404。本组件保留对 AI 分析结果的展示能力
      （数据由 projectApi 从工单服务读取），但不再提供无法工作的生成入口。
    -->

    <!-- PRD文档 -->
    <el-card v-if="project.aiPrd" class="analysis-section">
      <template #header>
        <span class="section-title">产品需求文档 (PRD)</span>
      </template>
      <div class="prd-content">
        <pre>{{ formatPrd(project.aiPrd) }}</pre>
      </div>
    </el-card>

    <!-- 工时预估 -->
    <el-card v-if="project.aiEstimatedHours || project.aiEstimatedDays" class="analysis-section">
      <template #header>
        <span class="section-title">工时预估</span>
      </template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="预估工时">{{ project.aiEstimatedHours }} 小时</el-descriptions-item>
        <el-descriptions-item label="预估天数">{{ project.aiEstimatedDays }} 天</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 风险分析 -->
    <el-card v-if="project.aiRiskAnalysis" class="analysis-section">
      <template #header>
        <span class="section-title">风险分析</span>
      </template>
      <div class="risk-content">{{ project.aiRiskAnalysis }}</div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import type { ProjectDTO } from '@/types'

// 父组件（ProjectDetail）整段包在 v-if="project" 里，所以到这里一定是非空的。
defineProps<{ project: ProjectDTO }>()

/** 后端 aiPrd 存的是一段 JSON 文本，字段名以 ai-service 生成时的输出为准。 */
interface PrdShape {
  projectName?: string
  overview?: string
  targetUsers?: string
  coreFeatures?: string[]
  techRequirements?: string
  risks?: string
}

const formatPrd = (prd: string) => {
  try {
    const data = JSON.parse(prd) as PrdShape
    return `项目名称: ${data.projectName || '-'}

概述: ${data.overview || '-'}

目标用户: ${data.targetUsers || '-'}

核心功能:
${(data.coreFeatures || []).map((f: string, i: number) => `  ${i + 1}. ${f}`).join('\n')}

技术要求: ${data.techRequirements || '-'}

潜在风险: ${data.risks || '-'}`
  } catch {
    // 不是 JSON 就直接原样显示 —— 用户至少能看到内容，而不是一片空白
    return prd
  }
}
</script>

<style scoped>
.ai-analysis h3 {
  margin: 0 0 16px 0;
}

.action-buttons {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}

.analysis-section {
  margin-bottom: 16px;
}

.section-title {
  font-weight: 600;
}

.prd-content pre {
  margin: 0;
  white-space: pre-wrap;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.6;
  color: #303133;
}

.risk-content {
  font-size: 13px;
  line-height: 1.6;
  color: #606266;
}
</style>
