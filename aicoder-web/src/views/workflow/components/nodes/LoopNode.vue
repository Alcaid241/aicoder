<script setup lang="ts">
import { Handle, Position } from '@vue-flow/core'
import type { NodeProps } from '@vue-flow/core'

const props = defineProps<NodeProps>()
</script>

<template>
  <div class="workflow-node loop-node" :class="{ selected: props.selected }">
    <Handle type="target" :position="Position.Top" />
    <div class="node-header">
      <span class="node-badge loop">循环</span>
      <span class="node-title">{{ props.data?.label || '循环' }}</span>
    </div>
    <div class="node-info">
      <span class="node-tag">最多 {{ props.data?.config?.maxIterations || 5 }} 次</span>
      <span v-if="props.data?.config?.exitCondition" class="node-tag">退出: {{ props.data.config.exitCondition }}</span>
    </div>
    <Handle type="source" :position="Position.Bottom" />
  </div>
</template>

<style scoped lang="scss">
.loop-node {
  background: var(--bg-surface); border: 2px solid var(--border-subtle); border-radius: 8px; padding: 12px 16px; min-width: 160px;
  &.selected { border-color: #3b82f6; box-shadow: 0 0 0 2px rgba(59,130,246,0.2); }
  .node-header { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
  .node-badge { font-size: 10px; font-weight: 700; padding: 2px 6px; border-radius: 4px; letter-spacing: 0.04em; &.loop { background: #cffafe; color: #0e7490; } }
  .node-title { font-size: 13px; font-weight: 600; color: var(--text-primary); }
  .node-info { display: flex; gap: 4px; }
  .node-tag { font-size: 11px; color: var(--text-muted); background: #f3f4f6; padding: 1px 6px; border-radius: 4px; }
}
</style>
