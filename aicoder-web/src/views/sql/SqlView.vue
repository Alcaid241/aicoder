<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { knowledgeApi } from '@/api/knowledge'
import { chatApi } from '@/api/chat'
import request from '@/api/request'
import type { KnowledgeBase, ModelInfo, SqlAskResponse, HistoryItem } from '@/types'

const ddlKnowledgeBases = ref<KnowledgeBase[]>([])
const models = ref<ModelInfo[]>([])
const selectedKbId = ref<number | null>(null)
const selectedModel = ref('')

const question = ref('')
const loading = ref(false)
const sqlResult = ref<SqlAskResponse | null>(null)
const executing = ref(false)
const executeResult = ref<{ columns: string[]; columnLabels: string[]; rows: any[][] } | null>(null)
const errorMsg = ref('')
const sqlCollapsed = ref(false)

const chatHistory = ref<HistoryItem[]>([])
const clarifyState = ref<{ question: string; options: string[]; multiSelect: boolean } | null>(null)
const selectedOptions = ref<Set<string>>(new Set())
const customInput = ref('')
const showCustomInput = ref(false)

const clarifyRounds = computed(() => {
  const rounds: { question: string; answer: string }[] = []
  for (let i = 0; i < chatHistory.value.length - 1; i += 2) {
    const assistant = chatHistory.value[i]
    const user = chatHistory.value[i + 1]
    if (assistant?.role === 'assistant' && user?.role === 'user') {
      rounds.push({ question: assistant.content, answer: user.content })
    }
  }
  return rounds
})

onMounted(async () => {
  try {
    const [kbRes, modelRes] = await Promise.all([
      knowledgeApi.list(),
      chatApi.getModels()
    ])
    ddlKnowledgeBases.value = kbRes.data.filter(kb => kb.type === 'DDL')
    models.value = modelRes.data
    if (models.value.length > 0) selectedModel.value = models.value[0].modelId
  } catch {
    // ignore
  }
})

const handleAsk = async () => {
  if (!question.value.trim() || !selectedKbId.value) return
  loading.value = true
  sqlResult.value = null
  clarifyState.value = null
  executeResult.value = null
  errorMsg.value = ''
  sqlCollapsed.value = false
  // 新问题清除旧的澄清历史
  chatHistory.value = []

  try {
    const res = await request.post<SqlAskResponse>('/rag/sql/ask', {
      knowledgeBaseId: selectedKbId.value,
      model: selectedModel.value,
      question: question.value.trim()
    })
    handleResponse(res.data)
  } catch (e: any) {
    errorMsg.value = e.response?.data?.message || '生成 SQL 失败'
  } finally {
    loading.value = false
  }
}

const handleResponse = (data: SqlAskResponse) => {
  if (data.status === 'CLARIFY' && data.options && data.options.length > 0) {
    // 每个澄清问题都强制追加一个「其他（自定义）」选项，允许用户手动输入想要的结果
    const opts = data.options.slice()
    if (!opts.some(isOtherOption)) opts.push('其他（自定义）')
    clarifyState.value = { question: data.clarification || '请选择', options: opts, multiSelect: !!data.multiSelect }
    selectedOptions.value = new Set()
  } else if (data.status === 'REJECTED' || data.isRejected) {
    sqlResult.value = data
  } else {
    sqlResult.value = data
  }
}

const isOtherOption = (opt: string) => /其他|请说明|自定义|补充/i.test(opt)

const handleOptionClick = async (option: string) => {
  if (!clarifyState.value) return

  if (isOtherOption(option)) {
    showCustomInput.value = true
    customInput.value = ''
    return
  }

  submitClarifyAnswer(option)
}

const handleCustomSubmit = async () => {
  const text = customInput.value.trim()
  if (!text) return
  showCustomInput.value = false
  if (clarifyState.value?.multiSelect) {
    // 多选：把手动输入的文本加入已选集合，用户可继续选或点「确认提交」
    const next = new Set(selectedOptions.value)
    next.add(text)
    selectedOptions.value = next
    customInput.value = ''
  } else {
    // 单选：直接以输入文本作为本轮答案提交
    submitClarifyAnswer(text)
  }
}

const handleMultiToggle = (opt: string) => {
  // 多选下点「其他」→ 弹出手动输入框（不把字面"其他"加入已选）
  if (isOtherOption(opt)) {
    showCustomInput.value = true
    customInput.value = ''
    return
  }
  const s = selectedOptions.value
  const next = new Set(s)
  next.has(opt) ? next.delete(opt) : next.add(opt)
  selectedOptions.value = next
}

const handleMultiSubmit = () => {
  if (selectedOptions.value.size === 0) return
  const answer = [...selectedOptions.value].join(', ')
  submitClarifyAnswer(answer)
}

const submitClarifyAnswer = async (answer: string) => {
  if (!clarifyState.value) return

  chatHistory.value.push({ role: 'assistant', content: clarifyState.value.question })
  chatHistory.value.push({ role: 'user', content: answer })
  clarifyState.value = null

  loading.value = true
  try {
    const res = await request.post<SqlAskResponse>('/rag/sql/ask', {
      knowledgeBaseId: selectedKbId.value,
      model: selectedModel.value,
      question: question.value.trim(),
      history: chatHistory.value
    })
    handleResponse(res.data)
  } catch (e: any) {
    errorMsg.value = e.response?.data?.message || '生成 SQL 失败'
  } finally {
    loading.value = false
  }
}

const handleNewQuestion = () => {
  chatHistory.value = []
  clarifyState.value = null
  sqlResult.value = null
  executeResult.value = null
  errorMsg.value = ''
  sqlCollapsed.value = false
}

const handleExecute = async () => {
  if (!sqlResult.value?.sql) return
  executing.value = true
  errorMsg.value = ''
  sqlCollapsed.value = true

  const kb = ddlKnowledgeBases.value.find(k => k.id === selectedKbId.value)
  try {
    const res = await request.post('/rag/sql/execute', {
      sql: sqlResult.value.sql,
      databaseName: kb?.databaseName || '',
      jdbcUrl: kb?.jdbcUrl || '',
      dbUsername: kb?.dbUsername || '',
      dbPassword: kb?.dbPassword || ''
    })
    const data = res.data
    if (data.columns && data.rows) {
      executeResult.value = data
    } else {
      errorMsg.value = '执行结果格式异常'
    }
  } catch (e: any) {
    errorMsg.value = e.response?.data?.message || 'SQL 执行失败'
  } finally {
    executing.value = false
  }
}


</script>

<template>
  <div class="sql-view">
    <div class="config-panel">
      <h3>NL2SQL 配置</h3>
      <div class="form-group">
        <label>DDL 知识库</label>
        <select v-model="selectedKbId">
          <option :value="null" disabled>请选择 DDL 知识库</option>
          <option v-for="kb in ddlKnowledgeBases" :key="kb.id" :value="kb.id">{{ kb.name }}</option>
        </select>
      </div>
      <div class="form-group">
        <label>模型</label>
        <select v-model="selectedModel">
          <option v-for="m in models" :key="m.modelId" :value="m.modelId">{{ m.modelName }}</option>
        </select>
      </div>
    </div>

    <div class="main-panel">
      <div class="input-section">
        <div class="form-group">
          <label>自然语言查询</label>
          <textarea
            v-model="question"
            rows="3"
            placeholder="请输入自然语言问题，例如：查询最近7天的销售总额"
            @keydown.enter.ctrl="handleAsk"
          />
        </div>
        <div class="input-actions">
          <button
            class="btn btn-primary"
            :disabled="!question.trim() || !selectedKbId || loading"
            @click="handleAsk"
          >
            {{ loading ? '思考中...' : '生成 SQL' }}
          </button>
          <button
            v-if="chatHistory.length > 0"
            class="btn btn-secondary"
            @click="handleNewQuestion"
          >重新提问</button>
        </div>
      </div>

      <div v-if="errorMsg" class="error-msg">{{ errorMsg }}</div>

      <div v-if="chatHistory.length > 0 || clarifyState" class="chat-section">
        <div class="chat-messages">
          <!-- 已完成的澄清轮次：折叠展示 -->
          <div v-for="(round, idx) in clarifyRounds" :key="idx" class="round-collapsed">
            <div class="round-q"><span class="round-label">Q:</span> {{ round.question }}</div>
            <div class="round-a"><span class="round-label">A:</span> {{ round.answer }}</div>
          </div>
          <!-- 当前澄清轮次：展开选项 -->
          <div v-if="clarifyState" class="chat-bubble bubble-clarify">
            <span class="bubble-icon">🤖</span>
            <div class="clarify-content">
              <p class="clarify-question">{{ clarifyState.question }}</p>
              <p v-if="clarifyState.multiSelect" class="clarify-hint">可多选</p>
              <!-- 多选：checkbox 列表 -->
              <div v-if="clarifyState.multiSelect" class="multi-select-list">
                <label
                  v-for="(opt, i) in clarifyState.options"
                  :key="i"
                  class="multi-select-item"
                  :class="{ 'multi-item-selected': selectedOptions.has(opt) }"
                >
                  <input
                    type="checkbox"
                    :checked="selectedOptions.has(opt)"
                    :disabled="loading"
                    @change="handleMultiToggle(opt)"
                  />
                  <span>{{ opt }}</span>
                </label>
                <div class="multi-select-actions">
                  <button
                    class="btn btn-multi-submit"
                    :disabled="selectedOptions.size === 0 || loading"
                    @click="handleMultiSubmit"
                  >确认提交</button>
                </div>
              </div>
              <!-- 单选：按钮 -->
              <div v-else class="clarify-options">
                <button
                  v-for="(opt, i) in clarifyState.options"
                  :key="i"
                  class="btn btn-clarify-option"
                  :class="{ 'btn-other-option': isOtherOption(opt) }"
                  :disabled="loading"
                  @click="handleOptionClick(opt)"
                >{{ opt }}</button>
              </div>
              <div v-if="showCustomInput" class="custom-input-row">
                <input
                  v-model="customInput"
                  type="text"
                  class="custom-input"
                  placeholder="请输入您的回答..."
                  :disabled="loading"
                  @keydown.enter="handleCustomSubmit"
                />
                <button
                  class="btn btn-custom-submit"
                  :disabled="!customInput.trim() || loading"
                  @click="handleCustomSubmit"
                >发送</button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div v-if="sqlResult" class="result-section">
        <div v-if="sqlResult.isRejected" class="rejected-card">
          <h4>请求被拒绝</h4>
          <p>{{ sqlResult.clarification || '该查询被系统拒绝，请调整问题后重试。' }}</p>
        </div>

        <div v-if="sqlResult.sql && sqlCollapsed" class="sql-card sql-card-collapsed" @click="sqlCollapsed = false">
          <div class="sql-collapsed-summary">
            <span class="sql-collapsed-label">SQL</span>
            <code class="sql-collapsed-text">{{ sqlResult.sql }}</code>
            <span class="sql-collapsed-hint">点击展开</span>
          </div>
        </div>

        <div v-if="sqlResult.sql && !sqlCollapsed" class="sql-card">
          <h4>生成的 SQL</h4>
          <pre class="sql-code"><code>{{ sqlResult.sql }}</code></pre>
          <div class="sql-actions">
            <button
              v-if="sqlResult.canExecute"
              class="btn btn-execute"
              :disabled="executing"
              @click="handleExecute"
            >
              <span class="execute-icon">▶</span>
              {{ executing ? '执行中...' : '执行 SQL' }}
            </button>
          </div>
        </div>

        <div v-if="executeResult" class="table-card">
          <h4>执行结果</h4>
          <div class="table-wrapper">
            <table>
              <thead>
                <tr>
                  <th v-for="(col, ci) in executeResult.columns" :key="col">{{ executeResult.columnLabels?.[ci] || col }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, ri) in executeResult.rows" :key="ri">
                  <td v-for="col in executeResult.columns" :key="col">{{ (row as unknown as Record<string, unknown>)[col] }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <p class="result-info">共 {{ executeResult.rows.length }} 条记录</p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.sql-view {
  height: 100%;
  display: flex;
  gap: 20px;
}

.config-panel {
  width: 280px;
  flex-shrink: 0;
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  padding: 20px;

  h3 {
    font-family: 'Sora', sans-serif;
    font-size: 16px;
    font-weight: 600;
    margin-bottom: 20px;
    color: var(--text-primary);
  }
}

.main-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
  overflow-y: auto;
}

.input-section {
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  padding: 20px;
}

.input-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.error-msg {
  background: var(--surface-danger);
  border: 1px solid rgba(196, 86, 74, 0.25);
  color: var(--danger);
  padding: 12px 16px;
  border-radius: var(--radius-sm);
  font-size: 13px;
}

.chat-section {
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  padding: 20px;
}

.chat-messages {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.round-collapsed {
  display: flex;
  align-items: baseline;
  gap: 12px;
  padding: 8px 14px;
  background: var(--bg-overlay);
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.5;
}

.round-q, .round-a {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 45%;
}

.round-label {
  font-weight: 600;
  margin-right: 4px;
  color: var(--text-muted);
}

.round-collapsed .round-q {
  color: var(--text-secondary);
}

.round-collapsed .round-a {
  color: var(--text-primary);
  font-weight: 500;
}

.chat-bubble {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  max-width: 85%;
}

.bubble-icon {
  font-size: 18px;
  flex-shrink: 0;
  margin-top: 2px;
}

.bubble-assistant {
  align-self: flex-start;
}

.bubble-user {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.bubble-text {
  padding: 8px 14px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.5;
}

.bubble-assistant .bubble-text {
  background: var(--bg-overlay);
  color: var(--text-primary);
  border-bottom-left-radius: 4px;
}

.bubble-user .bubble-text {
  background: #3b82f6;
  color: #fff;
  border-bottom-right-radius: 4px;
}

.bubble-clarify {
  align-self: flex-start;
  max-width: 90%;
}

.clarify-content {
  padding: 12px 16px;
  background: var(--bg-overlay);
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
  border-bottom-left-radius: 4px;
}

.clarify-question {
  font-size: 14px;
  color: var(--text-primary);
  margin-bottom: 10px;
}

.clarify-options {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.btn-clarify-option {
  display: flex;
  align-items: center;
  width: 100%;
  padding: 10px 14px;
  font-size: 13.5px;
  font-weight: 400;
  color: var(--text-primary);
  background: var(--bg-surface);
  border: 1px solid var(--border-default);
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
  text-align: left;
  line-height: 1.4;

  &:hover:not(:disabled) {
    background: var(--bg-hover);
    border-color: #3b82f6;
    box-shadow: 0 0 0 1px rgba(59, 130, 246, 0.15);
  }

  &:active:not(:disabled) {
    background: rgba(59, 130, 246, 0.08);
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}

.btn-other-option {
  color: #8b5cf6;
  background: rgba(139, 92, 246, 0.04);
  border-color: rgba(139, 92, 246, 0.25);

  &:hover:not(:disabled) {
    background: rgba(139, 92, 246, 0.08);
    border-color: #8b5cf6;
    box-shadow: 0 0 0 1px rgba(139, 92, 246, 0.15);
  }
}

.custom-input-row {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.custom-input {
  flex: 1;
  padding: 8px 12px;
  font-size: 13px;
  border: 1px solid var(--border-default);
  border-radius: 8px;
  background: var(--bg-surface);
  color: var(--text-primary);
  outline: none;
  transition: border-color 0.2s;

  &:focus {
    border-color: #3b82f6;
  }

  &:disabled {
    opacity: 0.5;
  }
}

.btn-custom-submit {
  padding: 8px 16px;
  font-size: 13px;
  font-weight: 500;
  color: #fff;
  background: #3b82f6;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  transition: opacity 0.2s;

  &:hover:not(:disabled) {
    opacity: 0.85;
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}

.clarify-hint {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 8px;
}

.multi-select-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.multi-select-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border: 1px solid var(--border-default);
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
  font-size: 13.5px;
  color: var(--text-primary);
  line-height: 1.4;

  &:hover {
    border-color: #3b82f6;
    background: var(--bg-hover);
    box-shadow: 0 0 0 1px rgba(59, 130, 246, 0.1);
  }

  &.multi-item-selected {
    border-color: #3b82f6;
    background: rgba(59, 130, 246, 0.06);
    box-shadow: 0 0 0 1px rgba(59, 130, 246, 0.15);

    span {
      font-weight: 500;
    }
  }

  input[type="checkbox"] {
    accent-color: #3b82f6;
    cursor: pointer;
    flex-shrink: 0;
  }
}

.multi-select-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.btn-multi-submit {
  padding: 8px 20px;
  font-size: 13px;
  font-weight: 500;
  color: #fff;
  background: #3b82f6;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  transition: opacity 0.2s;

  &:hover:not(:disabled) {
    opacity: 0.85;
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}

.result-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.rejected-card, .sql-card, .table-card {
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  padding: 20px;

  h4 {
    font-family: 'Sora', sans-serif;
    font-size: 15px;
    font-weight: 600;
    margin-bottom: 12px;
    color: var(--text-primary);
  }
}

.rejected-card p {
  color: var(--danger);
  font-size: 14px;
}

.sql-code {
  background: #1e293b;
  color: #e2e8f0;
  padding: 16px;
  border-radius: var(--radius-md);
  overflow-x: auto;
  font-family: 'JetBrains Mono', 'Menlo', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.5;
  margin-bottom: 12px;
}

.sql-card-collapsed {
  cursor: pointer;
  padding: 10px 16px;
  transition: all 0.2s;

  &:hover {
    border-color: var(--border-active);
    background: var(--bg-hover);
  }
}

.sql-collapsed-summary {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.sql-collapsed-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-muted);
  background: var(--bg-overlay);
  padding: 2px 8px;
  border-radius: 4px;
  flex-shrink: 0;
}

.sql-collapsed-text {
  flex: 1;
  font-size: 13px;
  font-family: 'JetBrains Mono', 'Menlo', 'Monaco', 'Courier New', monospace;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sql-collapsed-hint {
  font-size: 12px;
  color: var(--text-muted);
  flex-shrink: 0;
  opacity: 0;
  transition: opacity 0.2s;
}

.sql-card-collapsed:hover .sql-collapsed-hint {
  opacity: 1;
}

.sql-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.btn-execute {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 24px;
  font-size: 14px;
  font-weight: 500;
  color: #fff;
  background: linear-gradient(135deg, #22c55e 0%, #16a34a 100%);
  border: none;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  box-shadow: 0 2px 8px rgba(34, 197, 94, 0.3);

  &:hover:not(:disabled) {
    background: linear-gradient(135deg, #16a34a 0%, #15803d 100%);
    box-shadow: 0 4px 12px rgba(34, 197, 94, 0.45);
  }

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
}

.execute-icon {
  font-size: 10px;
}

.table-wrapper {
  overflow-x: auto;
  margin-bottom: 12px;

  table {
    width: 100%;
    border-collapse: collapse;
    font-size: 13px;

    th, td {
      border: 1px solid var(--border-default);
      padding: 8px 12px;
      text-align: left;
      white-space: nowrap;
    }
    th {
      background: var(--bg-overlay);
      font-weight: 600;
      color: var(--text-primary);
    }
    td { color: var(--text-primary); }
  }
}

.result-info {
  font-size: 13px;
  color: var(--text-muted);
}
</style>
