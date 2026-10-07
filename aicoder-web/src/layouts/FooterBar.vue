<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'

const beijingTime = ref('')
let timer: ReturnType<typeof setInterval> | null = null

const updateTime = () => {
  const now = new Date()
  const utc = now.getTime() + now.getTimezoneOffset() * 60000
  const beijing = new Date(utc + 8 * 3600000)
  const pad = (n: number) => n.toString().padStart(2, '0')
  beijingTime.value = `${beijing.getFullYear()}-${pad(beijing.getMonth() + 1)}-${pad(beijing.getDate())} ${pad(beijing.getHours())}:${pad(beijing.getMinutes())}:${pad(beijing.getSeconds())}`
}

onMounted(() => {
  updateTime()
  timer = setInterval(updateTime, 1000)
})
onUnmounted(() => { if (timer) clearInterval(timer) })
</script>

<template>
  <footer class="footer-bar">
    <span class="footer-time">{{ beijingTime }}</span>
    <span class="footer-tz">UTC+8</span>
  </footer>
</template>

<style scoped lang="scss">
.footer-bar {
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  background: var(--footer-bg);
  border-top: 1px solid var(--sidebar-border);
  flex-shrink: 0;
  transition: background 0.4s ease;
}

.footer-time {
  font-family: 'JetBrains Mono', 'Menlo', monospace;
  font-size: 11px;
  font-weight: 400;
  color: var(--footer-text);
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.02em;
}

.footer-tz {
  font-family: 'Sora', sans-serif;
  font-size: 10px;
  font-weight: 500;
  color: var(--text-muted);
  letter-spacing: 0.06em;
  padding: 1px 6px;
  border-radius: var(--radius-xs);
  background: var(--bg-overlay);
}
</style>
