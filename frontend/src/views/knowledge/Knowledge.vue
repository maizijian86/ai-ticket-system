<template>
  <div class="knowledge-page">
    <div class="page-header">
      <h2>知识库</h2>
    </div>

    <el-card class="filter-card glass-card-static">
      <el-form :inline="true" class="filter-form">
        <el-form-item label="搜索" class="glass-form-item">
          <el-input v-model="keyword" placeholder="搜索知识库..." clearable style="width: 300px" class="glass-input" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch" class="btn-glass btn-glass-primary">搜索</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card glass-card-static">
      <el-table :data="knowledgeList" v-loading="loading" class="glass-table">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="kb-title" @click="viewDetail(row)">{{ row.title }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="category" label="分类" width="120">
          <template #default="{ row }">
            <el-tag size="small" class="glass-tag">{{ row.category || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sourceType" label="来源" width="100">
          <template #default="{ row }">
            <span>{{ getSourceLabel(row.sourceType) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="viewCount" label="浏览" width="80" />
        <el-table-column prop="helpfulCount" label="有用" width="80" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="viewDetail(row)" class="btn-glass">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="detailVisible" title="知识详情" width="600px" class="glass-dialog">
      <div v-if="currentKnowledge" class="kb-detail">
        <h3>{{ currentKnowledge.title }}</h3>
        <div class="kb-meta">
          <el-tag size="small" class="glass-tag">{{ currentKnowledge.category || '-' }}</el-tag>
          <span class="meta-item">来源: {{ getSourceLabel(currentKnowledge.sourceType) }}</span>
          <span class="meta-item">浏览: {{ currentKnowledge.viewCount }}</span>
        </div>
        <div class="kb-content">{{ currentKnowledge.content }}</div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { aiApi } from '@/api'
import { ElMessage } from 'element-plus'

interface KnowledgeItem {
  id: number
  title: string
  content: string
  category?: string
  sourceType?: string
  viewCount: number
  helpfulCount: number
}

const knowledgeList = ref<KnowledgeItem[]>([])
const loading = ref(false)
const keyword = ref('')
const detailVisible = ref(false)
const currentKnowledge = ref<KnowledgeItem | null>(null)

async function loadKnowledge() {
  loading.value = true
  try {
    const res = await aiApi.searchKnowledge(keyword.value || '知识', 20)
    knowledgeList.value = res.map((item: any) => ({
      id: item.id,
      title: item.title,
      content: item.content,
      category: item.category,
      sourceType: item.sourceType,
      viewCount: item.viewCount || 0,
      helpfulCount: item.helpfulCount || 0
    }))
  } catch {
    ElMessage.error('加载知识库失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  loadKnowledge()
}

function viewDetail(item: KnowledgeItem) {
  currentKnowledge.value = item
  detailVisible.value = true
}

function getSourceLabel(source?: string) {
  const map: Record<string, string> = {
    ticket: '工单',
    manual: '手动',
    document: '文档'
  }
  return map[source || ''] || source || '-'
}

onMounted(() => {
  loadKnowledge()
})
</script>

<style scoped>
.page-header {
  margin-bottom: 24px;
}

.page-header h2 {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.5px;
}

.filter-card {
  margin-bottom: 20px;
}

.filter-form {
  display: flex;
  align-items: flex-end;
}

.table-card {
  margin-bottom: 20px;
}

.kb-title {
  color: var(--accent-info);
  cursor: pointer;
  font-weight: 500;
  transition: color 0.2s ease;
}

.kb-title:hover {
  color: var(--accent-primary);
}

.kb-detail h3 {
  font-size: 20px;
  margin-bottom: 20px;
  color: var(--text-primary);
  font-weight: 700;
}

.kb-meta {
  display: flex;
  gap: 16px;
  margin-bottom: 20px;
  font-size: 14px;
  align-items: center;
}

.meta-item {
  color: var(--text-tertiary);
}

.kb-content {
  font-size: 14px;
  line-height: 1.8;
  white-space: pre-wrap;
  color: var(--text-secondary);
}
</style>
