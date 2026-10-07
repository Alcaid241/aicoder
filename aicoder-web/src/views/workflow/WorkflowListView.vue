<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { workflowApi } from '@/api/workflow'
import type { Workflow } from '@/types/workflow'

const router = useRouter()
const workflows = ref<Workflow[]>([])
const loading = ref(false)
const activeTab = ref<'WORKFLOW' | 'CHAT'>('WORKFLOW')
const showCreateModal = ref(false)
const createName = ref('')
const createDesc = ref('')
const createType = ref<'WORKFLOW' | 'CHAT'>('WORKFLOW')
const showTemplateModal = ref(false)
const templates = ref<any[]>([])

const fetchTemplates = async () => {
  try {
    const res = await workflowApi.listTemplates()
    templates.value = res.data
  } catch { templates.value = [] }
}

const handleCloneTemplate = async (templateId: number) => {
  const res = await workflowApi.cloneFromTemplate(templateId)
  showTemplateModal.value = false
  router.push(`/workflow/${res.data.id}`)
}

const fetchList = async () => {
  loading.value = true
  try {
    const res = await workflowApi.list()
    workflows.value = res.data
  } finally { loading.value = false }
}

onMounted(() => { fetchList(); fetchTemplates() })

const filteredWorkflows = computed(() =>
  workflows.value.filter(wf => wf.type === activeTab.value)
)

const tabCount = (type: string) => workflows.value.filter(wf => wf.type === type).length

const handleCreate = async () => {
  if (!createName.value.trim()) return
  const graphData = JSON.stringify({
    nodes: [
      { id: 'start_1', type: 'start', position: { x: 250, y: 0 }, data: { label: '开始', config: {} } },
      { id: 'end_1', type: 'end', position: { x: 250, y: 400 }, data: { label: '结束', config: {} } }
    ],
    edges: []
  })
  const res = await workflowApi.create({
    name: createName.value,
    description: createDesc.value,
    type: createType.value,
    graphData
  })
  showCreateModal.value = false
  router.push(`/workflow/${res.data.id}`)
}

const handleDelete = async (id: number) => {
  if (!confirm('确定删除此工作流？')) return
  await workflowApi.delete(id)
  await fetchList()
}

const formatDate = (date: string) => {
  if (!date) return ''
  return new Date(date).toLocaleDateString('zh-CN')
}

const typeLabel = (type: string) => type === 'CHAT' ? '对话' : '工作流'
</script>

<template>
  <div class="workflow-list-view">
    <div class="page-topbar">
      <div>
        <h2>工作流</h2>
      </div>
      <button class="btn btn-primary" @click="showCreateModal = true; createName = ''; createDesc = ''; createType = activeTab">
        + 新建
      </button>
      <button class="btn btn-secondary" @click="showTemplateModal = true">
        从模板创建
      </button>
    </div>

    <div class="tabs">
      <button class="tab" :class="{ active: activeTab === 'WORKFLOW' }" @click="activeTab = 'WORKFLOW'">
        工作流 <span class="tab-count">{{ tabCount('WORKFLOW') }}</span>
      </button>
      <button class="tab" :class="{ active: activeTab === 'CHAT' }" @click="activeTab = 'CHAT'">
        对话工作流 <span class="tab-count">{{ tabCount('CHAT') }}</span>
      </button>
    </div>

    <div v-if="loading" class="empty-state">加载中...</div>

    <div v-else class="wf-grid">
      <div v-for="wf in filteredWorkflows" :key="wf.id" class="wf-card" @click="router.push(`/workflow/${wf.id}`)">
        <div class="wf-head">
          <h3>{{ wf.name }}</h3>
          <div class="wf-tags">
            <span class="tag tag-type" :class="wf.type === 'CHAT' ? 'tag-purple' : 'tag-blue'">
              {{ typeLabel(wf.type) }}
            </span>
            <span class="tag" :class="wf.status === 'DRAFT' ? 'tag-yellow' : 'tag-green'">
              {{ wf.status === 'DRAFT' ? '草稿' : '已发布' }}
            </span>
          </div>
        </div>
        <p class="wf-desc">{{ wf.description || '暂无描述' }}</p>
        <div class="wf-foot">
          <span class="stat">更新于 {{ formatDate(wf.updatedAt) }}</span>
          <button class="btn btn-ghost btn-sm" @click.stop="handleDelete(wf.id)" style="color: #ef4444">删除</button>
        </div>
      </div>

      <div v-if="filteredWorkflows.length === 0" class="empty-state">
        <span>暂无{{ activeTab === 'CHAT' ? '对话工作流' : '工作流' }}，点击上方按钮创建</span>
      </div>
    </div>

    <div v-if="showCreateModal" class="modal-overlay" @click.self="showCreateModal = false">
      <div class="modal-content">
        <h3>新建{{ createType === 'CHAT' ? '对话工作流' : '工作流' }}</h3>
        <div class="form-group">
          <label>类型</label>
          <div class="type-selector">
            <button class="type-btn" :class="{ active: createType === 'WORKFLOW' }" @click="createType = 'WORKFLOW'">
              <span class="type-icon">⚙️</span>
              <span class="type-label">工作流</span>
              <span class="type-desc">数据处理与自动化</span>
            </button>
            <button class="type-btn" :class="{ active: createType === 'CHAT' }" @click="createType = 'CHAT'">
              <span class="type-icon">💬</span>
              <span class="type-label">对话工作流</span>
              <span class="type-desc">节点处理后生成对话回复</span>
            </button>
          </div>
        </div>
        <div class="form-group">
          <label>名称</label>
          <input v-model="createName" placeholder="输入名称" @keyup.enter="handleCreate" />
        </div>
        <div class="form-group">
          <label>描述</label>
          <textarea v-model="createDesc" rows="2" placeholder="描述用途" />
        </div>
        <div class="modal-actions">
          <button class="btn btn-secondary" @click="showCreateModal = false">取消</button>
          <button class="btn btn-primary" :disabled="!createName.trim()" @click="handleCreate">创建</button>
        </div>
      </div>
    </div>

    <div v-if="showTemplateModal" class="modal-overlay" @click.self="showTemplateModal = false">
      <div class="modal-content">
        <h3>从模板创建</h3>
        <div class="template-list">
          <div v-for="tpl in templates" :key="tpl.id" class="template-card" @click="handleCloneTemplate(tpl.id)">
            <div class="tpl-head">
              <span class="tpl-name">{{ tpl.name }}</span>
              <span v-if="tpl.isSystem" class="tag tag-blue">系统</span>
            </div>
            <p class="tpl-desc">{{ tpl.description || '暂无描述' }}</p>
          </div>
          <div v-if="templates.length === 0" class="empty-state">暂无可用模板</div>
        </div>
        <div class="modal-actions">
          <button class="btn btn-secondary" @click="showTemplateModal = false">取消</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.workflow-list-view { height: 100%; display: flex; flex-direction: column; }
.page-topbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px;
  > div { display: flex; align-items: center; gap: 12px; }
  h2 { font-size: 20px; font-weight: 700; color: var(--text-primary); }
}

.tabs { display: flex; gap: 4px; margin-bottom: 20px; border-bottom: 1px solid var(--border-subtle); padding-bottom: 0; }
.tab {
  padding: 8px 16px; border: none; background: transparent; font-size: 14px; font-weight: 500;
  color: var(--text-muted); cursor: pointer; border-bottom: 2px solid transparent; transition: all 0.15s;
  display: flex; align-items: center; gap: 6px;
  &:hover { color: var(--text-secondary); }
  &.active { color: var(--text-primary); background: var(--bg-surface); border-bottom-color: #3b82f6; }
}
.tab-count { font-size: 11px; background: var(--bg-overlay); padding: 1px 6px; border-radius: 10px; }

.wf-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(340px, 1fr)); gap: 14px; }
.wf-card { background: var(--bg-surface); border: 1px solid var(--border-subtle); border-radius: 8px; padding: 20px; cursor: pointer; transition: border-color 0.2s, box-shadow 0.2s;
  &:hover { border-color: var(--border-default); box-shadow: 0 1px 3px rgba(0,0,0,0.08); }
}
.wf-head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 10px;
  h3 { font-size: 15px; font-weight: 600; color: var(--text-primary); }
}
.wf-tags { display: flex; gap: 4px; }
.wf-desc { font-size: 13px; color: var(--text-secondary); margin-bottom: 16px; }
.wf-foot { display: flex; justify-content: space-between; align-items: center; }
.stat { font-size: 12px; color: var(--text-muted); }

.btn { display: inline-flex; align-items: center; gap: 6px; padding: 6px 14px; border: 1px solid var(--border-subtle); border-radius: 6px; font-size: 13px; font-weight: 500; cursor: pointer; transition: all 0.15s;
  &.btn-primary { background: #3b82f6; color: #fff; border-color: transparent; &:disabled { opacity: 0.5; cursor: not-allowed; } }
  &.btn-secondary { background: var(--bg-overlay); color: var(--text-primary); }
  &.btn-ghost { background: transparent; border-color: transparent; color: var(--text-secondary); &:hover { background: var(--bg-overlay); } }
  &.btn-sm { padding: 4px 8px; font-size: 12px; }
}

.tag { display: inline-flex; align-items: center; padding: 2px 8px; border-radius: 4px; font-size: 11px; font-weight: 600;
  &.tag-yellow { background: #fef3c7; color: #92400e; }
  &.tag-green { background: #dcfce7; color: #15803d; }
  &.tag-blue { background: #dbeafe; color: #1d4ed8; }
  &.tag-purple { background: #f3e8ff; color: #7c3aed; }
}

.modal-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.4); display: flex; align-items: center; justify-content: center; z-index: 1000; }
.modal-content { background: var(--bg-surface); border-radius: 12px; padding: 24px; width: 480px; max-width: 90vw;
  h3 { font-size: 16px; font-weight: 600; margin-bottom: 20px; color: var(--text-primary); }
}
.form-group { margin-bottom: 16px;
  label { display: block; font-size: 12px; font-weight: 500; color: var(--text-secondary); margin-bottom: 4px; }
  input, textarea { width: 100%; padding: 8px 12px; border: 1px solid var(--border-subtle); border-radius: 6px; font-size: 13px; background: var(--bg-primary); color: var(--text-primary); }
}
.type-selector { display: flex; gap: 8px; }
.type-btn {
  flex: 1; display: flex; flex-direction: column; align-items: center; gap: 4px; padding: 14px 10px;
  border: 2px solid var(--border-subtle); border-radius: 8px; background: var(--bg-primary);
  cursor: pointer; transition: all 0.15s;
  &:hover { border-color: var(--border-default); }
  &.active { border-color: #3b82f6; background: rgba(59, 130, 246, 0.12); }
}
.type-icon { font-size: 20px; }
.type-label { font-size: 13px; font-weight: 600; color: var(--text-primary); }
.type-desc { font-size: 11px; color: var(--text-muted); }
.modal-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 20px; }
.empty-state { display: flex; flex-direction: column; align-items: center; gap: 12px; padding: 60px 0; color: var(--text-muted); font-size: 14px; }
.template-list { max-height: 400px; overflow-y: auto; display: flex; flex-direction: column; gap: 10px; }
.template-card {
  padding: 14px; border: 1px solid var(--border-subtle); border-radius: 8px;
  cursor: pointer; transition: border-color 0.2s;
  &:hover { border-color: #3b82f6; background: rgba(59, 130, 246, 0.08); }
}
.tpl-head { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.tpl-name { font-size: 14px; font-weight: 600; color: var(--text-primary); }
.tpl-desc { font-size: 12px; color: var(--text-secondary); margin: 0; }
</style>
