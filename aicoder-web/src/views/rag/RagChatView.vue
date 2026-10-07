<script setup lang="ts">
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { knowledgeApi } from '@/api/knowledge'
import { chatApi } from '@/api/chat'
import { ragChatApi } from '@/api/rag'
import { useUserStore } from '@/stores/userStore'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import type { KnowledgeBase, ModelInfo, ChatMessage } from '@/types'

const userStore = useUserStore()
const userDisplayName = computed(() =>
  userStore.user?.nickname || userStore.user?.username || '我'
)

const knowledgeBases = ref<KnowledgeBase[]>([])
const models = ref<ModelInfo[]>([])
const selectedKbId = ref<number | null>(null)
const selectedModel = ref('')
const messages = ref<ChatMessage[]>([])
const inputMessage = ref('')
const streamingContent = ref('')
const isStreaming = ref(false)
const sending = ref(false)
const messageListRef = ref<HTMLElement | null>(null)

const filteredKbs = () => knowledgeBases.value.filter(kb => kb.type === 'KB')

onMounted(async () => {
  try {
    const [kbRes, modelRes] = await Promise.all([
      knowledgeApi.list(),
      chatApi.getModels()
    ])
    knowledgeBases.value = kbRes.data
    models.value = modelRes.data
    if (models.value.length > 0) selectedModel.value = models.value[0].modelId
  } catch { /* ignore */ }
})

const scrollToBottom = async () => {
  await nextTick()
  if (messageListRef.value) {
    messageListRef.value.scrollTop = messageListRef.value.scrollHeight
  }
}

watch(streamingContent, () => scrollToBottom())

const handleSend = async () => {
  const msg = inputMessage.value.trim()
  if (!msg || sending.value || !selectedKbId.value) return

  sending.value = true
  inputMessage.value = ''
  messages.value.push({
    id: Date.now(),
    role: 'user',
    content: msg,
    modelId: selectedModel.value,
    createdAt: new Date().toISOString()
  })
  streamingContent.value = ''
  isStreaming.value = true
  await scrollToBottom()

  try {
    await ragChatApi.streamRagChat(
      selectedKbId.value!,
      selectedModel.value,
      msg,
      (_eventType, data) => {
        if (data && data !== '[DONE]') {
          streamingContent.value += data
        }
      },
      () => {
        streamingContent.value += '\n\n*连接出错，请重试*'
      }
    )
  } catch {
    streamingContent.value += '\n\n*连接出错，请重试*'
  }

  if (streamingContent.value) {
    messages.value.push({
      id: Date.now() + 1,
      role: 'assistant',
      content: streamingContent.value,
      modelId: selectedModel.value,
      createdAt: new Date().toISOString()
    })
  }
  streamingContent.value = ''
  isStreaming.value = false
  sending.value = false
}

const handleKeydown = (e: KeyboardEvent) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}

const formatTime = (t: string) => {
  if (!t) return ''
  const d = new Date(t)
  return `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`
}
</script>

<template>
  <div class="rag-view">
    <div class="rag-config">
      <div class="config-item">
        <label>知识库</label>
        <select v-model="selectedKbId">
          <option :value="null" disabled>选择知识库</option>
          <option v-for="kb in filteredKbs()" :key="kb.id" :value="kb.id">{{ kb.name }}</option>
        </select>
      </div>
      <div class="config-divider" />
      <div class="config-item">
        <label>模型</label>
        <select v-model="selectedModel">
          <option v-for="m in models" :key="m.modelId" :value="m.modelId">{{ m.modelName }}</option>
        </select>
      </div>
    </div>

    <div class="rag-chat">
      <div class="msg-list" ref="messageListRef">
        <div v-if="messages.length === 0 && !isStreaming" class="empty-chat">
          <div class="empty-icon">
            <svg width="48" height="48" viewBox="0 0 48 48"><circle cx="24" cy="24" r="22" fill="none" stroke="currentColor" stroke-width="1.2" opacity="0.4"/><circle cx="24" cy="24" r="8" fill="none" stroke="currentColor" stroke-width="1.2" opacity="0.6"/><line x1="24" y1="14" x2="24" y2="18" stroke="currentColor" stroke-width="1.2" opacity="0.6" stroke-linecap="round"/><line x1="24" y1="30" x2="24" y2="34" stroke="currentColor" stroke-width="1.2" opacity="0.6" stroke-linecap="round"/></svg>
          </div>
          <p v-if="selectedKbId">输入问题开始 RAG 对话</p>
          <p v-else>选择一个知识库开始</p>
        </div>

        <div v-for="msg in messages" :key="msg.id" class="msg-row" :class="msg.role">
          <div class="msg-avatar">
            <svg v-if="msg.role !== 'assistant'" width="18" height="18" viewBox="0 0 18 18"><circle cx="9" cy="6" r="3.5" fill="none" stroke="currentColor" stroke-width="1.4"/><path d="M2 17c0-3.9 3.1-7 7-7s7 3.1 7 7" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/></svg>
            <svg v-else width="18" height="18" viewBox="0 0 18 18"><rect x="2" y="2" width="14" height="14" rx="3" fill="none" stroke="currentColor" stroke-width="1.4"/><circle cx="9" cy="9" r="2.5" fill="currentColor"/></svg>
          </div>
          <div class="msg-body">
            <div class="msg-meta">
              <span class="msg-role">{{ msg.role !== 'assistant' ? userDisplayName : 'AI Coder' }}</span>
              <span class="msg-time">{{ formatTime(msg.createdAt) }}</span>
            </div>
            <div class="msg-bubble">
              <MarkdownRenderer v-if="msg.role === 'assistant'" :content="msg.content" />
              <p v-else>{{ msg.content }}</p>
            </div>
          </div>
        </div>

        <div v-if="isStreaming" class="msg-row assistant">
          <div class="msg-avatar">
            <svg width="18" height="18" viewBox="0 0 18 18"><rect x="2" y="2" width="14" height="14" rx="3" fill="none" stroke="currentColor" stroke-width="1.4"/><circle cx="9" cy="9" r="2.5" fill="currentColor"/></svg>
          </div>
          <div class="msg-body">
            <div class="msg-meta">
              <span class="msg-role">AI Coder</span>
              <span class="streaming-badge">检索生成中</span>
            </div>
            <div class="msg-bubble">
              <MarkdownRenderer :content="streamingContent" />
              <span class="cursor-blink">▌</span>
            </div>
          </div>
        </div>
      </div>

      <div class="chat-input-area">
        <div class="input-wrapper">
          <textarea
            v-model="inputMessage"
            :placeholder="selectedKbId ? '输入问题，Enter 发送，Shift+Enter 换行...' : '请先选择知识库'"
            @keydown="handleKeydown"
            rows="2"
            :disabled="sending || !selectedKbId"
          />
          <button
            class="btn btn-primary send-btn"
            :disabled="!inputMessage.trim() || sending || !selectedKbId"
            @click="handleSend"
          >
            <svg v-if="!sending" width="16" height="16" viewBox="0 0 16 16"><line x1="2" y1="8" x2="14" y2="8" stroke="currentColor" stroke-width="2" stroke-linecap="round"/><polyline points="10,4 14,8 10,12" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
            <svg v-else width="16" height="16" viewBox="0 0 16 16" class="spinner"><circle cx="8" cy="8" r="6" fill="none" stroke="currentColor" stroke-width="1.5" stroke-dasharray="28" stroke-dashoffset="8"/></svg>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.rag-view {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--bg-surface);
  border-radius: var(--radius-lg);
  border: 1px solid var(--border-subtle);
  overflow: hidden;
}

.rag-config {
  display: flex;
  align-items: center;
  gap: 0;
  padding: 12px 20px;
  border-bottom: 1px solid var(--border-subtle);
  background: var(--bg-surface);
}

.config-item {
  display: flex;
  align-items: center;
  gap: 10px;
  label {
    font-family: 'Sora', sans-serif;
    font-size: 12px;
    font-weight: 600;
    color: var(--text-muted);
    letter-spacing: 0.04em;
    text-transform: uppercase;
    white-space: nowrap;
  }
  select {
    background: var(--bg-overlay);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-sm);
    padding: 6px 28px 6px 10px;
    font-size: 13px;
    font-family: 'Sora', sans-serif;
    font-weight: 500;
    color: var(--text-primary);
    cursor: pointer;
    appearance: none;
    background-image: url("data:image/svg+xml,%3Csvg width='10' height='6' viewBox='0 0 10 6' xmlns='http://www.w3.org/2000/svg'%3E%3Cpath d='M1 1l4 4 4-4' fill='none' stroke='%236b6560' stroke-width='1.5' stroke-linecap='round'/%3E%3C/svg%3E");
    background-repeat: no-repeat;
    background-position: right 8px center;
    min-width: 160px;
    &:focus { border-color: var(--accent); }
  }
}

.config-divider {
  width: 1px;
  height: 24px;
  background: var(--border-subtle);
  margin: 0 20px;
}

.rag-chat {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.msg-list {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}

.empty-chat {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: 16px;
  color: var(--text-muted);
  p { font-size: 14px; }
}

.msg-row {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
  animation: fadeIn 0.2s ease;
}

.msg-avatar {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
  background: var(--bg-overlay);
  border: 1px solid var(--border-subtle);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: var(--text-secondary);
}

.msg-body {
  min-width: 0;
  flex: 1;
}

.msg-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.msg-role {
  font-family: 'Sora', sans-serif;
  font-size: 11px;
  font-weight: 600;
  color: var(--text-secondary);
  letter-spacing: 0.02em;
}

.msg-time {
  font-size: 10px;
  color: var(--text-muted);
  font-family: 'JetBrains Mono', monospace;
}

.msg-bubble {
  display: inline-block;
  padding: 8px 14px;
  border-radius: var(--radius-md);
  line-height: 1.55;
  font-size: 14px;
  max-width: 70%;
  overflow-wrap: break-word;
  word-break: break-word;
  vertical-align: top;

  p {
    margin: 0;
    white-space: pre-wrap;
  }
}

// --- User messages (right side) ---
.msg-row.user {
  flex-direction: row-reverse;

  .msg-meta {
    justify-content: flex-end;
    flex-direction: row-reverse;
    gap: 8px;
  }

  .msg-body {
    text-align: right;
  }

  .msg-avatar {
    background: var(--accent-glow);
    border-color: rgba(212, 160, 64, 0.25);
    color: var(--accent);
  }

  .msg-bubble {
    background: var(--bubble-user-bg);
    border: 1px solid var(--bubble-user-border);
    border-right: 3px solid var(--accent);
    text-align: left;
    border-radius: var(--radius-md) 4px var(--radius-md) var(--radius-md);
  }
}

// --- Assistant messages (left side) ---
.msg-row.assistant {
  .msg-bubble {
    display: block;
    max-width: 70%;
    overflow-x: auto;
    background: var(--bubble-assistant-bg);
    border: 1px solid var(--bubble-assistant-border);
  }
}

.streaming-badge {
  font-family: 'Sora', sans-serif;
  font-size: 10px;
  font-weight: 500;
  color: var(--accent);
  animation: pulse 2s infinite;
  padding: 2px 8px;
  background: var(--accent-glow);
  border-radius: var(--radius-full);
  letter-spacing: 0.04em;
}

.cursor-blink {
  animation: blink 0.8s step-end infinite;
  color: var(--accent);
  font-weight: 300;
}

.chat-input-area {
  padding: 16px 20px;
  border-top: 1px solid var(--border-subtle);
  background: var(--bg-surface);
}

.input-wrapper {
  display: flex;
  gap: 10px;
  align-items: flex-end;

  textarea {
    flex: 1;
    padding: 12px 16px;
    border-radius: var(--radius-md);
    resize: none;
    font-size: 14px;
    line-height: 1.5;
    background: var(--input-bg);
    border: 1px solid var(--input-border);
    color: var(--text-primary);
    &::placeholder { color: var(--text-muted); }
    &:focus {
      border-color: var(--accent);
      box-shadow: 0 0 0 3px var(--input-focus-ring);
    }
  }
}

.send-btn {
  height: 42px;
  width: 42px;
  padding: 0;
  border-radius: var(--radius-sm);
  flex-shrink: 0;

  .spinner { animation: spin 1.2s linear infinite; }
}

@keyframes spin { to { transform: rotate(360deg); } }
</style>
