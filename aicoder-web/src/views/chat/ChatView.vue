<script setup lang="ts">
import { ref, computed, nextTick, onMounted, watch } from 'vue'
import { useChatStore } from '@/stores/chatStore'
import { useUserStore } from '@/stores/userStore'
import { streamRequest } from '@/api/request'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'

const chatStore = useChatStore()
const userStore = useUserStore()

const inputMessage = ref('')
const messageListRef = ref<HTMLElement | null>(null)
const sending = ref(false)

const userDisplayName = computed(() =>
  userStore.user?.nickname || userStore.user?.username || '我'
)

const scrollToBottom = async () => {
  await nextTick()
  if (messageListRef.value) {
    messageListRef.value.scrollTop = messageListRef.value.scrollHeight
  }
}

watch(() => chatStore.streamingContent, () => { scrollToBottom() })
watch(() => chatStore.messages.length, () => { scrollToBottom() })

onMounted(async () => {
  await chatStore.fetchConversations()
  await chatStore.fetchModels()
})

const handleNewChat = () => {
  chatStore.resetChat()
  inputMessage.value = ''
}

const handleSelectConversation = async (id: number) => {
  chatStore.selectConversation(id)
  await scrollToBottom()
}

const handleDeleteConversation = async (e: Event, id: number) => {
  e.stopPropagation()
  await chatStore.deleteConversation(id)
}

const streamChat = async (message: string) => {
  try {
    await streamRequest('/api/chat/stream', {
      model: chatStore.currentModel,
      message: message,
      conversationId: chatStore.currentConversationId
    }, {
      onEvent(eventType, data) {
        if (eventType === 'conversation') {
          try {
            const parsed = JSON.parse(data)
            if (parsed.conversationId && !chatStore.currentConversationId) {
              chatStore.currentConversationId = parsed.conversationId
            }
          } catch { /* ignore */ }
        } else if (data && data !== '[DONE]') {
          chatStore.appendStreamContent(data)
        }
      },
      onError() {
        chatStore.finishStreaming()
      },
      onComplete() {
        chatStore.finishStreaming()
        chatStore.fetchConversations()
      }
    })
  } catch {
    chatStore.finishStreaming()
  }
}

const handleSend = async () => {
  const msg = inputMessage.value.trim()
  if (!msg || sending.value) return

  sending.value = true
  inputMessage.value = ''
  await chatStore.createAndSend(chatStore.currentModel, msg)
  await scrollToBottom()

  await streamChat(msg)
  chatStore.finishStreaming()
  sending.value = false
  await chatStore.fetchConversations()
}

const handleKeydown = (e: KeyboardEvent) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}

const streamAgent = async (message: string) => {
  chatStore.startAgentChat(chatStore.currentModel, message)
  await scrollToBottom()
  try {
    await streamRequest('/api/chat/agent/stream', {
      model: chatStore.currentModel,
      message: message,
      conversationId: chatStore.currentConversationId
    }, {
      onEvent(eventType, data) {
        try {
          const parsed = JSON.parse(data)
          if (eventType === 'conversation') {
            if (parsed.conversationId && !chatStore.currentConversationId) {
              chatStore.currentConversationId = parsed.conversationId
            }
          } else if (eventType === 'agent_think') {
            chatStore.addAgentStep({ type: 'think', content: parsed.content })
          } else if (eventType === 'tool_call') {
            chatStore.addAgentStep({
              type: 'tool_call',
              tool: parsed.tool,
              args: parsed.args,
              thought: parsed.thought
            })
          } else if (eventType === 'tool_result') {
            chatStore.addAgentStep({
              type: 'tool_result',
              tool: parsed.tool,
              result: parsed.result
            })
          } else if (eventType === 'agent_answer') {
            chatStore.streamingContent = parsed.content || ''
          } else if (eventType === 'done') {
            chatStore.finishStreaming()
            chatStore.fetchConversations()
          }
        } catch { /* ignore parse error */ }
        scrollToBottom()
      },
      onError() {
        chatStore.finishStreaming()
      },
      onComplete() {
        if (chatStore.isStreaming) {
          chatStore.finishStreaming()
        }
      }
    })
  } catch {
    chatStore.finishStreaming()
  }
}

const handleSendAgent = async () => {
  const msg = inputMessage.value.trim()
  if (!msg || sending.value) return
  sending.value = true
  inputMessage.value = ''
  await streamAgent(msg)
  chatStore.finishStreaming()
  sending.value = false
  await chatStore.fetchConversations()
}

const formatTime = (t: string) => {
  if (!t) return ''
  const d = new Date(t)
  return `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`
}
</script>

<template>
  <div class="chat-view">
    <!-- Conversation Sidebar -->
    <aside class="conv-sidebar">
      <div class="conv-header">
        <h3>会话</h3>
        <button class="btn btn-ghost btn-sm" @click="handleNewChat">
          <svg width="16" height="16" viewBox="0 0 16 16"><line x1="8" y1="2" x2="8" y2="14" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/><line x1="2" y1="8" x2="14" y2="8" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>
        </button>
      </div>
      <div class="conv-list">
        <button
          v-for="conv in chatStore.conversations" :key="conv.id"
          class="conv-item"
          :class="{ active: chatStore.currentConversationId === conv.id }"
          @click="handleSelectConversation(conv.id)"
        >
          <div class="conv-body">
            <span class="conv-title">{{ conv.title || '新对话' }}</span>
            <span class="conv-model">{{ conv.modelId }}</span>
          </div>
          <span class="conv-del" @click="(e) => handleDeleteConversation(e, conv.id)" title="删除">
            <svg width="12" height="12" viewBox="0 0 12 12"><line x1="3" y1="6" x2="9" y2="6" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/></svg>
          </span>
        </button>
        <div v-if="chatStore.conversations.length === 0" class="empty-state">
          <svg width="36" height="36" viewBox="0 0 24 24" style="color: var(--text-muted)"><circle cx="12" cy="8" r="4" fill="none" stroke="currentColor" stroke-width="1.4"/><path d="M4 20c0-4.4 3.6-8 8-8s8 3.6 8 8" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/></svg>
          <span>暂无会话</span>
        </div>
      </div>
    </aside>

    <!-- Chat Panel -->
    <div class="chat-panel">
      <!-- Header -->
      <div class="chat-topbar">
        <div class="model-picker">
          <svg width="14" height="14" viewBox="0 0 14 14" style="color: var(--accent)"><circle cx="7" cy="7" r="5.5" fill="none" stroke="currentColor" stroke-width="1.4"/><circle cx="7" cy="7" r="2" fill="currentColor"/></svg>
          <select v-model="chatStore.currentModel">
            <option v-for="m in chatStore.models" :key="m.modelId" :value="m.modelId">{{ m.modelName }}</option>
          </select>
        </div>
        <span class="conv-context" v-if="chatStore.currentConversationId">
          会话 #{{ chatStore.currentConversationId }}
        </span>
      </div>

      <!-- Messages -->
      <div class="msg-list" ref="messageListRef">
        <div v-if="chatStore.messages.length === 0 && !chatStore.isStreaming" class="empty-chat">
          <div class="empty-icon">
            <svg width="48" height="48" viewBox="0 0 48 48"><circle cx="24" cy="24" r="22" fill="none" stroke="currentColor" stroke-width="1.2" opacity="0.4"/><circle cx="24" cy="18" r="7" fill="none" stroke="currentColor" stroke-width="1.2" opacity="0.6"/><path d="M13 39c0-6.1 4.9-11 11-11s11 4.9 11 11" fill="none" stroke="currentColor" stroke-width="1.2" opacity="0.6"/></svg>
          </div>
          <p>开始一段对话</p>
        </div>

        <div v-for="msg in chatStore.messages" :key="msg.id" class="msg-row" :class="msg.role">
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

        <!-- Streaming -->
        <div v-if="chatStore.isStreaming" class="msg-row assistant">
          <div class="msg-avatar">
            <svg width="18" height="18" viewBox="0 0 18 18"><rect x="2" y="2" width="14" height="14" rx="3" fill="none" stroke="currentColor" stroke-width="1.4"/><circle cx="9" cy="9" r="2.5" fill="currentColor"/></svg>
          </div>
          <div class="msg-body">
            <div class="msg-meta">
              <span class="msg-role">AI Coder</span>
              <span class="streaming-badge">生成中</span>
            </div>
            <div class="msg-bubble">
              <MarkdownRenderer :content="chatStore.streamingContent" />
              <span class="cursor-blink">▌</span>
            </div>
          </div>
        </div>

        <!-- Agent Steps -->
        <div v-if="chatStore.isAgentMode && chatStore.agentSteps.length > 0" class="agent-steps">
          <div v-for="(step, idx) in chatStore.agentSteps" :key="idx" class="agent-step">
            <div v-if="step.type === 'think'" class="step-think">
              <span class="step-icon">&#x1f4ad;</span>
              <span class="step-label">思考</span>
              <span class="step-text">{{ step.content }}</span>
            </div>
            <div v-else-if="step.type === 'tool_call'" class="step-tool-call">
              <span class="step-icon">&#x1f527;</span>
              <span class="step-label">调用工具: {{ step.tool }}</span>
              <span v-if="step.thought" class="step-text">{{ step.thought }}</span>
            </div>
            <div v-else-if="step.type === 'tool_result'" class="step-tool-result">
              <span class="step-icon">&#x1f4ca;</span>
              <span class="step-label">{{ step.tool }} 返回结果</span>
              <details><summary>查看详情</summary><pre class="step-pre">{{ step.result }}</pre></details>
            </div>
          </div>
        </div>
      </div>

      <!-- Input -->
      <div class="chat-input-area">
        <div class="input-wrapper">
          <textarea
            v-model="inputMessage"
            placeholder="输入消息，Enter 发送，Shift+Enter 换行..."
            @keydown="handleKeydown"
            rows="2"
            :disabled="sending"
          />
          <button
            class="btn btn-primary send-btn"
            :disabled="!inputMessage.trim() || sending"
            @click="handleSend"
          >
            <svg v-if="!sending" width="16" height="16" viewBox="0 0 16 16"><line x1="2" y1="8" x2="14" y2="8" stroke="currentColor" stroke-width="2" stroke-linecap="round"/><polyline points="10,4 14,8 10,12" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
            <svg v-else width="16" height="16" viewBox="0 0 16 16" class="spinner"><circle cx="8" cy="8" r="6" fill="none" stroke="currentColor" stroke-width="1.5" stroke-dasharray="28" stroke-dashoffset="8"/></svg>
          </button>
          <button
            class="btn btn-agent"
            :disabled="!inputMessage.trim() || sending"
            @click="handleSendAgent"
            title="Agent 模式：AI 自主调用工具完成任务"
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2L2 7l10 5 10-5-10-5z"/><path d="M2 17l10 5 10-5"/><path d="M2 12l10 5 10-5"/></svg>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.chat-view {
  display: flex;
  height: 100%;
  background: var(--bg-surface);
  border-radius: var(--radius-lg);
  border: 1px solid var(--border-subtle);
  overflow: hidden;
}

// --- Sidebar ---
.conv-sidebar {
  width: 260px;
  background: var(--sidebar-bg);
  border-right: 1px solid var(--sidebar-border);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  transition: background 0.4s ease;
}

.conv-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border-bottom: 1px solid var(--sidebar-border);

  h3 {
    font-family: 'Sora', sans-serif;
    font-size: 13px;
    font-weight: 600;
    letter-spacing: 0.04em;
    text-transform: uppercase;
    color: var(--text-muted);
  }
}

.conv-list {
  flex: 1;
  overflow-y: auto;
  padding: 6px;
}

.conv-item {
  width: 100%;
  display: flex;
  align-items: center;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-primary);
  text-align: left;
  transition: all 0.12s ease;
  margin-bottom: 1px;

  &:hover { background: var(--bg-hover); }
  &.active { background: var(--sidebar-active-bg); }

  .conv-del {
    display: none;
    padding: 4px;
    color: var(--text-muted);
    border-radius: var(--radius-xs);
    flex-shrink: 0;
    &:hover { color: var(--danger); background: var(--surface-danger); }
  }
  &:hover .conv-del { display: flex; }
}

.conv-body {
  flex: 1;
  min-width: 0;
  .conv-title {
    display: block;
    font-size: 13px;
    font-weight: 500;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    color: var(--text-primary);
  }
  .conv-model {
    font-size: 11px;
    color: var(--text-muted);
    margin-top: 2px;
    display: block;
    font-family: 'Sora', sans-serif;
  }
}

// --- Chat Panel ---
.chat-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.chat-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 20px;
  border-bottom: 1px solid var(--border-subtle);
  background: var(--bg-surface);
}

.model-picker {
  display: flex;
  align-items: center;
  gap: 8px;
  select {
    background: var(--bg-overlay);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-sm);
    padding: 5px 28px 5px 10px;
    font-size: 13px;
    font-family: 'Sora', sans-serif;
    font-weight: 500;
    color: var(--text-primary);
    cursor: pointer;
    appearance: none;
    background-image: url("data:image/svg+xml,%3Csvg width='10' height='6' viewBox='0 0 10 6' xmlns='http://www.w3.org/2000/svg'%3E%3Cpath d='M1 1l4 4 4-4' fill='none' stroke='%236b6560' stroke-width='1.5' stroke-linecap='round'/%3E%3C/svg%3E");
    background-repeat: no-repeat;
    background-position: right 8px center;
    &:focus { border-color: var(--accent); }
  }
}

.conv-context {
  font-size: 11px;
  color: var(--text-muted);
  font-family: 'Sora', sans-serif;
}

// --- Messages ---
.msg-list {
  flex: 1;
  overflow-y: auto;
  padding: 24px 28px;
  scroll-behavior: smooth;
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

// --- Input ---
.chat-input-area {
  padding: 14px 20px;
  border-top: 1px solid var(--border-subtle);
  background: var(--bg-surface);
}

.input-wrapper {
  display: flex;
  gap: 10px;
  align-items: flex-end;

  textarea {
    flex: 1;
    padding: 11px 16px;
    border-radius: var(--radius-md);
    resize: none;
    font-size: 14px;
    line-height: 1.5;
    background: var(--input-bg);
    border: 1px solid var(--input-border);
    color: var(--text-primary);
    transition: border-color 0.2s ease, box-shadow 0.2s ease;
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

.btn-agent {
  height: 42px; width: 42px; padding: 0; border-radius: var(--radius-sm);
  flex-shrink: 0; background: #8b5cf6; color: #fff; border: none;
  cursor: pointer; display: flex; align-items: center; justify-content: center;
  &:hover { background: #7c3aed; }
  &:disabled { opacity: 0.5; cursor: not-allowed; }
}

.agent-steps {
  margin: 0 24px 16px; padding: 16px; background: var(--bg-overlay);
  border: 1px solid var(--border-subtle); border-radius: 8px;
}

.agent-step {
  padding: 8px 0; border-bottom: 1px solid var(--border-subtle);
  &:last-child { border-bottom: none; }
}

.step-think, .step-tool-call, .step-tool-result {
  display: flex; align-items: flex-start; gap: 8px; flex-wrap: wrap;
}

.step-icon { font-size: 16px; flex-shrink: 0; }
.step-label { font-size: 12px; font-weight: 600; color: var(--text-secondary); white-space: nowrap; }
.step-text { font-size: 12px; color: var(--text-secondary); flex: 1; min-width: 0; white-space: pre-wrap; word-break: break-word; line-height: 1.6; }
.step-pre {
  font-size: 11px; color: var(--text-primary); background: var(--bg-surface);
  padding: 8px; border-radius: 4px; overflow-x: auto; max-height: 200px;
  overflow-y: auto; white-space: pre-wrap; word-break: break-all; margin: 4px 0 0;
}
</style>
