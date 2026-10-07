<script setup lang="ts">
const nodeItems = [
  {
    type: 'llm',
    label: 'LLM 对话',
    badge: 'LLM',
    badgeClass: 'llm',
    defaultConfig: { model: 'deepseek-v4-flash', promptTemplate: '请回答以下问题：{{input}}', inputKey: 'query', outputKey: 'result' }
  },
  {
    type: 'rag',
    label: '知识检索',
    badge: 'RAG',
    badgeClass: 'rag',
    defaultConfig: { knowledgeBaseId: '', topK: 5, inputKey: 'query', outputKey: 'context' }
  },
  {
    type: 'nl2sql',
    label: 'NL2SQL',
    badge: 'SQL',
    badgeClass: 'sql',
    defaultConfig: { knowledgeBaseId: '', model: 'deepseek-v4-flash', databaseName: '', inputKey: 'query', outputKey: 'sqlResult' }
  },
  {
    type: 'condition',
    label: '条件分支',
    badge: '条件',
    badgeClass: 'cond',
    defaultConfig: { inputKey: 'result', conditions: [{ label: '分支A', match: '' }, { label: '默认', match: '__default__' }] }
  },
  {
    type: 'tool',
    label: 'HTTP 请求',
    badge: 'HTTP',
    badgeClass: 'tool',
    defaultConfig: { url: '', method: 'GET', headers: {}, bodyTemplate: '', inputKey: 'query', outputKey: 'httpResult' }
  },
  {
    type: 'subworkflow',
    label: '子工作流',
    badge: '子流',
    badgeClass: 'sub',
    defaultConfig: { workflowId: '', inputKey: 'query', outputKey: 'subResult' }
  },
  {
    type: 'loop',
    label: '循环',
    badge: '循环',
    badgeClass: 'loop',
    defaultConfig: { maxIterations: 5, exitCondition: '', inputKey: 'query', outputKey: 'loopResult' }
  }
]

const onDragStart = (event: DragEvent, type: string, defaultConfig: Record<string, any>) => {
  if (!event.dataTransfer) return
  event.dataTransfer.setData('application/vueflow', JSON.stringify({ type, defaultConfig }))
  event.dataTransfer.effectAllowed = 'move'
}
</script>

<template>
  <div class="node-panel">
    <h4>节点</h4>
    <div class="node-list">
      <div
        v-for="item in nodeItems"
        :key="item.type"
        class="node-item"
        draggable="true"
        @dragstart="onDragStart($event, item.type, item.defaultConfig)"
      >
        <span class="badge" :class="item.badgeClass">{{ item.badge }}</span>
        <span>{{ item.label }}</span>
      </div>
    </div>
    <p class="panel-hint">拖拽节点到右侧画布</p>
  </div>
</template>

<style scoped lang="scss">
.node-panel {
  width: 180px;
  padding: 16px 12px;
  border-right: 1px solid var(--border-subtle);
  background: var(--bg-surface);
  overflow-y: auto;
  h4 { font-size: 12px; font-weight: 600; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.06em; margin-bottom: 12px; }
}
.node-list { display: flex; flex-direction: column; gap: 6px; }
.node-item {
  display: flex; align-items: center; gap: 8px; padding: 8px 10px;
  border: 1px solid var(--border-subtle); border-radius: 6px; background: var(--bg-primary);
  cursor: grab; font-size: 13px; color: var(--text-primary); transition: border-color 0.15s;
  user-select: none;
  &:hover { border-color: var(--border-default); }
  &:active { cursor: grabbing; }
}
.badge {
  font-size: 9px; font-weight: 700; padding: 2px 5px; border-radius: 3px; letter-spacing: 0.04em;
  &.llm { background: #dbeafe; color: #1d4ed8; }
  &.rag { background: #dcfce7; color: #15803d; }
  &.sql { background: #e0e7ff; color: #4338ca; }
  &.cond { background: #fef3c7; color: #92400e; }
  &.tool { background: #fce7f3; color: #9d174d; }
  &.sub { background: #ede9fe; color: #6d28d9; }
  &.loop { background: #cffafe; color: #0e7490; }
}
.panel-hint {
  margin-top: 12px;
  font-size: 11px;
  color: var(--text-muted);
  text-align: center;
}
</style>
