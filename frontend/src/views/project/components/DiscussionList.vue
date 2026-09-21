<template>
  <div class="discussion-list">
    <h3>讨论区</h3>

    <!-- 发表评论 -->
    <div class="comment-input">
      <el-input
        v-model="newComment"
        type="textarea"
        :rows="3"
        placeholder="输入你的评论..."
      />
      <el-button type="primary" @click="submitComment" :loading="submitting" :disabled="!newComment.trim()">
        发表评论
      </el-button>
    </div>

    <!-- 评论列表 -->
    <div class="comment-list">
      <div v-for="comment in discussions" :key="comment.id" class="comment-item">
        <div class="comment-header">
          <span class="comment-user">{{ comment.userName }}</span>
          <span class="comment-time">{{ formatTime(comment.createdAt) }}</span>
        </div>
        <div class="comment-content">{{ comment.content }}</div>
        <div v-if="comment.isAiSuggested" class="ai-badge">
          <el-tag type="success" size="small">AI建议</el-tag>
        </div>
      </div>

      <el-empty v-if="discussions.length === 0" description="暂无讨论" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { projectApi } from '@/api'

const props = defineProps({
  projectId: [String, Number],
  targetType: String,
  targetId: [String, Number]
})

const discussions = ref([])
const newComment = ref('')
const submitting = ref(false)

const loadDiscussions = async () => {
  try {
    if (props.targetType && props.targetId) {
      const res = await projectApi.getTargetDiscussions(props.targetType, props.targetId)
      discussions.value = res.data || []
    } else if (props.projectId) {
      const res = await projectApi.getProjectDiscussions(props.projectId)
      discussions.value = res.data || []
    }
  } catch (error) {
    console.error('加载讨论失败', error)
  }
}

const submitComment = async () => {
  if (!newComment.value.trim()) return

  submitting.value = true
  try {
    await projectApi.addDiscussion(props.projectId, {
      targetType: props.targetType,
      targetId: props.targetId,
      content: newComment.value
    })
    newComment.value = ''
    ElMessage.success('评论发表成功')
    loadDiscussions()
  } catch (error) {
    ElMessage.error('发表失败')
  } finally {
    submitting.value = false
  }
}

const formatTime = (time) => {
  if (!time) return ''
  const date = new Date(time)
  const now = new Date()
  const diff = now - date

  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  return date.toLocaleDateString('zh-CN')
}

onMounted(() => {
  loadDiscussions()
})
</script>

<style scoped>
.discussion-list h3 {
  margin: 0 0 16px 0;
}

.comment-input {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 24px;
}

.comment-input .el-button {
  align-self: flex-end;
}

.comment-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.comment-item {
  padding: 16px;
  background: #f5f7fa;
  border-radius: 8px;
}

.comment-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.comment-user {
  font-weight: 600;
  color: #303133;
}

.comment-time {
  font-size: 12px;
  color: #909399;
}

.comment-content {
  color: #606266;
  line-height: 1.6;
  white-space: pre-wrap;
}

.ai-badge {
  margin-top: 8px;
}
</style>
