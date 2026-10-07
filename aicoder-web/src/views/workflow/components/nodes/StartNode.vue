<script setup lang="ts">
import { Handle, Position } from '@vue-flow/core'
</script>

<template>
  <div class="workflow-node start-node">
    <Handle type="source" :position="Position.Bottom" />
    <div class="node-icon">▶</div>
    <div class="node-label">开始</div>
  </div>
</template>

<style scoped lang="scss">
.start-node {
  background: var(--bg-surface);
  border: 2px solid var(--border-default);
  border-radius: 24px;
  padding: 8px 20px;
  display: flex;
  align-items: center;
  gap: 8px;
  .node-icon { color: #10b981; font-size: 14px; }
  .node-label { font-size: 13px; font-weight: 600; color: var(--text-primary); }
}
</style>
