<script setup lang="ts">
import { Handle, Position } from '@vue-flow/core'
import type { NodeProps } from '@vue-flow/core'

const props = defineProps<NodeProps>()
const conditions = props.data?.config?.conditions || []
</script>

<template>
  <div class="workflow-node condition-node" :class="{ selected: props.selected }">
    <Handle type="target" :position="Position.Top" />
    <div class="node-header">
      <span class="node-badge cond">条件</span>
      <span class="node-title">{{ props.data?.label || '条件分支' }}</span>
    </div>
    <div v-if="conditions.length > 0" class="branch-list">
      <div v-for="cond in conditions" :key="cond.label" class="branch-item">
        <span class="branch-label">{{ cond.label }}</span>
        <span class="branch-match">{{ cond.match === '__default__' ? '其他' : cond.match }}</span>
        <Handle type="source" :position="Position.Right" :id="cond.label" class="branch-handle" />
      </div>
    </div>
    <div v-else class="node-info">
      <span class="node-tag">未配置分支</span>
      <Handle type="source" :position="Position.Bottom" id="__default__" />
    </div>
  </div>
</template>

<style scoped lang="scss">
.condition-node {
  background: var(--bg-surface); border: 2px solid var(--border-subtle); border-radius: 8px; padding: 12px 16px; min-width: 180px;
  &.selected { border-color: #3b82f6; box-shadow: 0 0 0 2px rgba(59,130,246,0.2); }
  .node-header { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
  .node-badge { font-size: 10px; font-weight: 700; padding: 2px 6px; border-radius: 4px; letter-spacing: 0.04em; &.cond { background: #fef3c7; color: #92400e; } }
  .node-title { font-size: 13px; font-weight: 600; color: var(--text-primary); }
  .node-info { display: flex; gap: 4px; }
  .node-tag { font-size: 11px; color: var(--text-muted); background: #f3f4f6; padding: 1px 6px; border-radius: 4px; }
}
.branch-list { display: flex; flex-direction: column; gap: 6px; }
.branch-item {
  display: flex; align-items: center; justify-content: space-between; gap: 8px;
  padding: 4px 8px; background: #fefce8; border: 1px solid #fde68a; border-radius: 4px;
  position: relative;
  .branch-label { font-size: 12px; font-weight: 600; color: #92400e; }
  .branch-match { font-size: 11px; color: var(--text-muted); }
}
.branch-handle {
  position: absolute !important;
  right: -20px;
  top: 50%;
  transform: translateY(-50%);
  width: 10px;
  height: 10px;
}
</style>
