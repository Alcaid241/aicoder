<script setup lang="ts">
import { Handle, Position } from '@vue-flow/core'
import { computed } from 'vue'

const props = defineProps<{ data?: Record<string, any> }>()

const hasReply = computed(() => !!props.data?.config?.replyRequirements)
</script>

<template>
  <div class="workflow-node end-node" :class="{ 'has-reply': hasReply }">
    <Handle type="target" :position="Position.Top" />
    <div class="node-icon">■</div>
    <div class="node-label">结束</div>
    <span v-if="hasReply" class="reply-dot" title="已配置回复要求"></span>
  </div>
</template>

<style scoped lang="scss">
.end-node {
  background: var(--bg-surface);
  border: 2px solid var(--border-default);
  border-radius: 24px;
  padding: 8px 20px;
  display: flex;
  align-items: center;
  gap: 8px;
  .node-icon { color: #ef4444; font-size: 14px; }
  .node-label { font-size: 13px; font-weight: 600; color: var(--text-primary); }
  &.has-reply { border-color: #8b5cf6; }
}
.reply-dot {
  width: 7px; height: 7px; border-radius: 50%; background: #8b5cf6;
  margin-left: 2px; flex-shrink: 0;
}
</style>
