<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { dashboardApi, type DashboardStats } from '@/api/dashboard'

const router = useRouter()
const stats = ref<DashboardStats | null>(null)
const loading = ref(true)

onMounted(async () => {
  try {
    const res = await dashboardApi.getStats()
    stats.value = res.data
  } catch { /* 忽略 */ } finally {
    loading.value = false
  }
})

const statCards = [
  { key: 'conversationCount' as const, label: '对话数', color: '#3b82f6', icon: '💬' },
  { key: 'knowledgeBaseCount' as const, label: '知识库', color: '#8b5cf6', icon: '📚' },
  { key: 'workflowCount' as const, label: '工作流', color: '#f59e0b', icon: '⚙️' },
  { key: 'executionCount' as const, label: '执行次数', color: '#10b981', icon: '▶️' },
]

const quickActions = [
  { label: '新建对话', path: '/chat', color: '#3b82f6' },
  { label: '新建知识库', path: '/knowledge', color: '#8b5cf6' },
  { label: '新建工作流', path: '/workflow', color: '#f59e0b' },
]
</script>

<template>
  <div class="home-view">
    <div class="home-header">
      <div class="greeting">
        <h1>AI Coder <span class="vibe-badge">Vibe Coding</span></h1>
        <p>一站式 AI 开发助手平台</p>
        <p class="vibe-desc">本平台由人类提出需求，AI 独立完成全部架构设计、代码编写与功能实现</p>
      </div>
      <div class="quick-actions">
        <button
          v-for="action in quickActions" :key="action.path"
          class="quick-btn"
          :style="{ '--accent': action.color }"
          @click="router.push(action.path)"
        >
          {{ action.label }}
          <svg width="14" height="14" viewBox="0 0 16 16"><polyline points="6,3 11,8 6,13" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
        </button>
      </div>
    </div>

    <div v-if="loading" class="loading">加载中...</div>

    <div v-else class="stats-grid">
      <div
        v-for="card in statCards" :key="card.key"
        class="stat-card"
        :style="{ '--card-color': card.color }"
      >
        <div class="stat-icon">{{ card.icon }}</div>
        <div class="stat-info">
          <span class="stat-value">{{ stats?.[card.key] ?? 0 }}</span>
          <span class="stat-label">{{ card.label }}</span>
        </div>
      </div>
    </div>

    <div class="module-section">
      <h3>功能模块</h3>
      <div class="module-grid">
        <div class="module-card" @click="router.push('/chat')">
          <span class="mod-icon" style="color: #3b82f6;">💬</span>
          <div>
            <h4>智能对话</h4>
            <p>多模型实时对话，流式输出</p>
          </div>
        </div>
        <div class="module-card" @click="router.push('/knowledge')">
          <span class="mod-icon" style="color: #8b5cf6;">📚</span>
          <div>
            <h4>知识库</h4>
            <p>文档上传与向量化检索</p>
          </div>
        </div>
        <div class="module-card" @click="router.push('/rag')">
          <span class="mod-icon" style="color: #10b981;">🔍</span>
          <div>
            <h4>RAG 对话</h4>
            <p>基于知识库的检索增强生成</p>
          </div>
        </div>
        <div class="module-card" @click="router.push('/workflow')">
          <span class="mod-icon" style="color: #f59e0b;">⚙️</span>
          <div>
            <h4>工作流</h4>
            <p>可视化 DAG 工作流编排</p>
          </div>
        </div>
        <div class="module-card" @click="router.push('/sql')">
          <span class="mod-icon" style="color: #06b6d4;">🗄️</span>
          <div>
            <h4>NL2SQL</h4>
            <p>自然语言转 SQL 查询</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.home-view {
  max-width: 960px;
  margin: 0 auto;
  height: 100%;
  overflow-y: auto;
}

.home-header {
  margin-bottom: 28px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--border-subtle);
}

.greeting {
  h1 { font-size: 24px; font-weight: 700; color: var(--text-primary); margin-bottom: 4px; display: flex; align-items: center; gap: 10px; }
  p { font-size: 14px; color: var(--text-secondary); }
}

.vibe-badge {
  display: inline-flex; align-items: center; gap: 4px;
  font-size: 11px; font-weight: 600; letter-spacing: 0.06em;
  padding: 3px 10px; border-radius: 20px;
  background: linear-gradient(135deg, #8b5cf6 0%, #6366f1 50%, #3b82f6 100%);
  color: #fff; vertical-align: middle;
  animation: vibe-glow 3s ease-in-out infinite;
}

.vibe-desc {
  font-size: 12px !important;
  color: var(--text-muted) !important;
  font-style: italic;
  margin-top: 6px !important;
}

@keyframes vibe-glow {
  0%, 100% { box-shadow: 0 0 8px rgba(99, 102, 241, 0.3); }
  50% { box-shadow: 0 0 16px rgba(139, 92, 246, 0.5); }
}

.quick-actions { display: flex; gap: 10px; margin-top: 16px; }

.quick-btn {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 8px 16px; border-radius: 8px;
  border: 1px solid var(--accent); background: transparent;
  color: var(--accent); font-size: 13px; font-weight: 600; cursor: pointer;
  transition: all 0.18s ease;
  &:hover { background: var(--accent); color: #fff; }
}

.loading { text-align: center; color: var(--text-muted); padding: 40px 0; }

.stats-grid {
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 32px;
}

.stat-card {
  display: flex; align-items: center; gap: 14px;
  padding: 18px 20px; background: var(--bg-surface);
  border: 1px solid var(--border-subtle); border-radius: 10px;
  border-left: 3px solid var(--card-color);
}

.stat-icon { font-size: 28px; }

.stat-info {
  .stat-value { display: block; font-size: 22px; font-weight: 700; color: var(--text-primary); }
  .stat-label { display: block; font-size: 12px; color: var(--text-muted); margin-top: 2px; }
}

.module-section {
  h3 { font-size: 15px; font-weight: 600; color: var(--text-primary); margin-bottom: 14px; }
}

.module-grid {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 12px;
}

.module-card {
  display: flex; align-items: center; gap: 12px;
  padding: 16px; background: var(--bg-surface);
  border: 1px solid var(--border-subtle); border-radius: 10px;
  cursor: pointer; transition: border-color 0.2s, transform 0.15s;
  &:hover { border-color: var(--border-default); transform: translateY(-1px); }
  h4 { font-size: 14px; font-weight: 600; color: var(--text-primary); margin-bottom: 2px; }
  p { font-size: 12px; color: var(--text-secondary); margin: 0; }
}

.mod-icon { font-size: 24px; flex-shrink: 0; }
</style>
