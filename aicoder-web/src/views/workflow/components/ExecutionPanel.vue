<script setup lang="ts">
import { ref } from 'vue'
import { useWorkflowStore } from '@/stores/workflowStore'

const store = useWorkflowStore()
const inputValue = ref('')
const expandedNodes = ref<Set<string>>(new Set())
const panelExpanded = ref(false)

const handleRun = () => {
  if (!inputValue.value.trim()) return
  store.executeWorkflow({ query: inputValue.value })
  panelExpanded.value = true
}

const toggleNode = (nodeId: string) => {
  if (expandedNodes.value.has(nodeId)) {
    expandedNodes.value.delete(nodeId)
  } else {
    expandedNodes.value.add(nodeId)
  }
}

const formatDuration = (start: number, end?: number) => {
  if (!end) return ''
  const ms = end - start
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

const statusLabel = (status: string) => {
  if (status === 'running') return '运行中'
  if (status === 'completed') return '完成'
  return '失败'
}

const statusIcon = (status: string) => {
  if (status === 'running') return '⟳'
  if (status === 'completed') return '✓'
  return '✗'
}
</script>

<template>
  <div class="execution-panel" :class="{ expanded: panelExpanded }">
    <div class="exec-input">
      <input v-model="inputValue" placeholder="输入查询内容..." @keyup.enter="handleRun" :disabled="store.executing" />
      <button class="btn btn-primary" @click="handleRun" :disabled="store.executing || !inputValue.trim()">
        {{ store.executing ? '执行中...' : '运行' }}
      </button>
      <button v-if="store.nodeExecList.length > 0" class="btn btn-ghost" @click="panelExpanded = !panelExpanded">
        {{ panelExpanded ? '收起' : '展开' }}
      </button>
    </div>

    <div v-if="store.nodeExecList.length > 0" class="exec-timeline">
      <div
        v-for="item in store.nodeExecList"
        :key="item.nodeId"
        class="timeline-item"
        :class="item.status"
      >
        <div class="timeline-header" @click="toggleNode(item.nodeId)">
          <span class="status-icon" :class="item.status">{{ statusIcon(item.status) }}</span>
          <span class="node-name">{{ item.nodeName }}</span>
          <span class="node-duration">{{ formatDuration(item.startTime, item.endTime) }}</span>
          <span class="node-status-badge" :class="item.status">{{ statusLabel(item.status) }}</span>
          <span class="expand-icon">{{ expandedNodes.has(item.nodeId) ? '▾' : '▸' }}</span>
        </div>
        <div v-if="expandedNodes.has(item.nodeId)" class="timeline-detail">
          <div v-if="item.error" class="detail-error">{{ item.error }}</div>
          <div v-if="item.output" class="detail-output">
            <pre>{{ typeof item.output === 'string' ? item.output : JSON.stringify(item.output, null, 2) }}</pre>
          </div>
        </div>
      </div>
    </div>

    <div v-if="store.executionOutput" class="exec-result">
      <div class="result-label">最终输出</div>
      <pre>{{ store.executionOutput }}</pre>
    </div>

    <div v-if="store.executionError" class="exec-error">
      <span class="error-icon">✗</span>
      <span>{{ store.executionError }}</span>
    </div>
  </div>
</template>

<style scoped lang="scss">
.execution-panel {
  border-top: 1px solid var(--border-subtle);
  background: var(--bg-surface);
  padding: 12px 16px;
  max-height: 200px;
  overflow-y: auto;
  transition: max-height 0.3s ease;

  &.expanded { max-height: 50vh; }
}

.exec-input {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;

  input {
    flex: 1;
    padding: 6px 12px;
    border: 1px solid var(--border-subtle);
    border-radius: 6px;
    font-size: 13px;
    background: var(--bg-primary);
    color: var(--text-primary);
    outline: none;
    &:focus { border-color: #3b82f6; }
  }
}

.btn {
  white-space: nowrap;
  padding: 6px 14px;
  border: none;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
  &:disabled { opacity: 0.5; cursor: not-allowed; }

  &.btn-primary { background: #3b82f6; color: #fff; &:hover:not(:disabled) { opacity: 0.9; } }
  &.btn-ghost { background: transparent; color: var(--text-secondary); border: 1px solid var(--border-subtle); &:hover { background: var(--bg-hover); } }
}

.exec-timeline { display: flex; flex-direction: column; gap: 6px; margin-bottom: 10px; }

.timeline-item {
  border-radius: 8px;
  border: 1px solid var(--border-subtle);
  overflow: hidden;

  &.running { border-color: #3b82f640; background: #3b82f608; }
  &.completed { border-color: #10b98140; background: #10b98108; }
  &.failed { border-color: #ef444440; background: #ef444408; }
}

.timeline-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  cursor: pointer;
  font-size: 13px;
  &:hover { background: var(--bg-hover); }
}

.status-icon {
  font-size: 14px;
  font-weight: 700;
  &.running { color: #3b82f6; animation: spin 1s linear infinite; }
  &.completed { color: #10b981; }
  &.failed { color: #ef4444; }
}

.node-name { flex: 1; font-weight: 500; color: var(--text-primary); }

.node-duration {
  font-size: 12px;
  color: var(--text-muted);
  font-variant-numeric: tabular-nums;
}

.node-status-badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  font-weight: 500;
  &.running { background: #3b82f620; color: #3b82f6; }
  &.completed { background: #10b98120; color: #10b981; }
  &.failed { background: #ef444420; color: #ef4444; }
}

.expand-icon { color: var(--text-muted); font-size: 12px; }

.timeline-detail {
  padding: 0 12px 10px 36px;
  font-size: 12px;
}

.detail-error {
  color: #ef4444;
  background: #ef444410;
  padding: 6px 10px;
  border-radius: 6px;
  margin-bottom: 6px;
}

.detail-output pre {
  background: #f3f4f6;
  padding: 8px;
  border-radius: 6px;
  overflow-x: auto;
  color: var(--text-primary);
  margin: 0;
  white-space: pre-wrap;
  font-size: 12px;
  max-height: 150px;
  overflow-y: auto;
}

.exec-result {
  margin-top: 8px;
  border-radius: 8px;
  border: 1px solid #10b98140;
  background: #10b98108;
  overflow: hidden;

  .result-label {
    font-size: 12px;
    font-weight: 600;
    color: #10b981;
    padding: 6px 12px;
    background: #10b98110;
  }

  pre {
    padding: 10px 12px;
    margin: 0;
    font-size: 13px;
    white-space: pre-wrap;
    color: var(--text-primary);
    max-height: 200px;
    overflow-y: auto;
  }
}

.exec-error {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  padding: 8px 12px;
  border-radius: 8px;
  background: #ef444410;
  border: 1px solid #ef444440;
  color: #ef4444;
  font-size: 13px;

  .error-icon { font-weight: 700; }
}

@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
</style>
