<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useWorkflowStore } from '@/stores/workflowStore'
import FlowCanvas from './components/FlowCanvas.vue'
import NodePanel from './components/NodePanel.vue'
import ConfigPanel from './components/ConfigPanel.vue'
import ExecutionPanel from './components/ExecutionPanel.vue'

const route = useRoute()
const store = useWorkflowStore()

const saving = ref(false)
const saveMsg = ref('')

const isChat = computed(() => store.currentWorkflow?.type === 'CHAT')

onMounted(() => {
  const id = Number(route.params.id)
  if (id) store.fetchWorkflow(id)
})

const handleSave = async () => {
  saving.value = true
  saveMsg.value = ''
  try {
    await store.saveWorkflow()
    saveMsg.value = '已保存'
    setTimeout(() => { saveMsg.value = '' }, 2000)
  } catch (e: any) {
    saveMsg.value = '保存失败: ' + (e.response?.data?.message || e.message || '未知错误')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="workflow-editor">
    <div class="editor-topbar">
      <div class="topbar-left">
        <router-link to="/workflow" class="back-link" title="返回列表">
          <svg width="16" height="16" viewBox="0 0 16 16"><polyline points="10,3 5,8 10,13" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>
        </router-link>
        <h3>{{ store.currentWorkflow?.name || '工作流' }}</h3>
        <span class="type-badge" :class="isChat ? 'chat' : 'workflow'">{{ isChat ? '对话工作流' : '工作流' }}</span>
      </div>
      <div class="topbar-actions">
        <span v-if="saveMsg" class="save-msg" :class="{ error: saveMsg.includes('失败') }">{{ saveMsg }}</span>
        <button class="btn btn-primary" :disabled="saving" @click="handleSave">
          {{ saving ? '保存中...' : '保存' }}
        </button>
      </div>
    </div>

    <div class="editor-body">
      <NodePanel />
      <div class="canvas-area">
        <FlowCanvas />
      </div>
      <ConfigPanel />
    </div>
    <ExecutionPanel />
  </div>
</template>

<style scoped lang="scss">
.workflow-editor { height: 100%; display: flex; flex-direction: column; }
.editor-topbar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 8px 16px; border-bottom: 1px solid var(--border-subtle); background: var(--bg-surface);
  h3 { font-size: 15px; font-weight: 600; color: var(--text-primary); }
}
.topbar-left { display: flex; align-items: center; gap: 10px; }
.back-link {
  display: flex; align-items: center; justify-content: center;
  width: 28px; height: 28px; border-radius: 6px;
  color: var(--text-secondary); text-decoration: none;
  &:hover { background: var(--bg-hover); color: var(--text-primary); }
}
.topbar-actions { display: flex; align-items: center; gap: 10px; }
.save-msg {
  font-size: 12px; font-weight: 500; color: #10b981;
  &.error { color: #ef4444; }
}
.type-badge {
  font-size: 11px; font-weight: 600; padding: 2px 8px; border-radius: 4px;
  &.workflow { background: #dbeafe; color: #1d4ed8; }
  &.chat { background: #f3e8ff; color: #7c3aed; }
}
.editor-body { flex: 1; display: flex; overflow: hidden; }
.canvas-area { flex: 1; overflow: hidden; }

.btn {
  display: inline-flex; align-items: center; gap: 6px; padding: 6px 14px;
  border: none; border-radius: 6px; font-size: 13px; cursor: pointer;
  &.btn-primary {
    background: #3b82f6; color: #fff;
    &:hover { opacity: 0.9; }
    &:disabled { opacity: 0.5; cursor: not-allowed; }
  }
}
</style>
