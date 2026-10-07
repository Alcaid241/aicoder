<script setup lang="ts">
import { computed, ref, watch, watchEffect } from 'vue'
import { useWorkflowStore } from '@/stores/workflowStore'
import { knowledgeApi } from '@/api/knowledge'
import type { KnowledgeBase } from '@/types'

const store = useWorkflowStore()
const node = computed(() => store.selectedNode())
const knowledgeBases = ref<KnowledgeBase[]>([])

watchEffect(async () => {
  if (node.value?.type === 'rag' || node.value?.type === 'nl2sql') {
    try {
      const res = await knowledgeApi.list()
      knowledgeBases.value = res.data || []
    } catch { knowledgeBases.value = [] }
  }
})

const ragKnowledgeBases = computed(() => knowledgeBases.value.filter(kb => kb.type !== 'DDL'))
const ddlKnowledgeBases = computed(() => knowledgeBases.value.filter(kb => kb.type === 'DDL'))

const updateConfig = (key: string, value: any) => {
  if (!node.value) return
  store.updateNodeConfig(node.value.id, { [key]: value })
}

const handleDeleteNode = () => {
  if (!node.value) return
  store.removeNode(node.value.id)
}

const updateCondition = (idx: number, field: string, value: string) => {
  if (!node.value) return
  const conditions = [...(node.value.data.config.conditions || [])]
  if (field === 'match' && !value) value = '__default__'
  conditions[idx] = { ...conditions[idx], [field]: value }
  updateConfig('conditions', conditions)
}
const addCondition = () => {
  if (!node.value) return
  const conditions = [...(node.value.data.config.conditions || [])]
  conditions.push({ label: `分支${conditions.length + 1}`, match: '' })
  updateConfig('conditions', conditions)
}
const removeCondition = (idx: number) => {
  if (!node.value) return
  const conditions = [...(node.value.data.config.conditions || [])]
  conditions.splice(idx, 1)
  updateConfig('conditions', conditions)
}
const headersJson = computed(() => {
  if (!node.value?.data?.config?.headers) return '{}'
  return JSON.stringify(node.value.data.config.headers, null, 2)
})
const updateHeaders = (value: string) => {
  try { updateConfig('headers', JSON.parse(value)) } catch { /* ignore */ }
}

// end 节点回复要求
const replyText = ref('')
watch(() => node.value?.data?.config?.replyRequirements, (val) => {
  replyText.value = val || ''
}, { immediate: true })
watch(replyText, (val) => {
  if (node.value?.type === 'end') {
    store.updateNodeData(node.value.id, {
      ...node.value.data,
      config: { ...node.value.data?.config, replyRequirements: val }
    })
  }
})
</script>

<template>
  <div class="config-panel">
    <template v-if="node">
      <h4>节点配置</h4>
      <div class="form-group">
        <label>名称</label>
        <input :value="node.data.label" @input="node.data.label = ($event.target as HTMLInputElement).value" />
      </div>

      <template v-if="node.type === 'llm'">
        <div class="form-group">
          <label>模型</label>
          <select :value="node.data.config.model" @change="updateConfig('model', ($event.target as HTMLSelectElement).value)">
            <option value="deepseek-v4-flash">DeepSeek V4 Flash</option>
            <option value="gemma3:4b">Gemma3 4B</option>
            <option value="deepseek-coder">DeepSeek Coder</option>
          </select>
        </div>
        <div class="form-group">
          <label>Prompt 模板</label>
          <textarea rows="4" :value="node.data.config.promptTemplate"
            @input="updateConfig('promptTemplate', ($event.target as HTMLTextAreaElement).value)" />
          <p class="hint">使用 &#123;&#123;input&#125;&#125; 作为输入变量占位符</p>
        </div>
        <div class="form-group">
          <label>输入键</label>
          <input :value="node.data.config.inputKey" @input="updateConfig('inputKey', ($event.target as HTMLInputElement).value)" />
        </div>
        <div class="form-group">
          <label>输出键</label>
          <input :value="node.data.config.outputKey" @input="updateConfig('outputKey', ($event.target as HTMLInputElement).value)" />
        </div>
      </template>

      <template v-if="node.type === 'rag'">
        <div class="form-group">
          <label>知识库</label>
          <select :value="node.data.config.knowledgeBaseId" @change="updateConfig('knowledgeBaseId', ($event.target as HTMLSelectElement).value)">
            <option value="">请选择知识库</option>
            <option v-for="kb in ragKnowledgeBases" :key="kb.id" :value="String(kb.id)">{{ kb.name }}</option>
          </select>
        </div>
        <div class="form-group">
          <label>Top K</label>
          <input type="number" :value="node.data.config.topK" @input="updateConfig('topK', parseInt(($event.target as HTMLInputElement).value))" />
        </div>
        <div class="form-group">
          <label>输入键</label>
          <input :value="node.data.config.inputKey" @input="updateConfig('inputKey', ($event.target as HTMLInputElement).value)" />
        </div>
        <div class="form-group">
          <label>输出键</label>
          <input :value="node.data.config.outputKey" @input="updateConfig('outputKey', ($event.target as HTMLInputElement).value)" />
        </div>
      </template>

      <template v-if="node.type === 'nl2sql'">
        <div class="form-group">
          <label>模型</label>
          <select :value="node.data.config.model" @change="updateConfig('model', ($event.target as HTMLSelectElement).value)">
            <option value="deepseek-v4-flash">DeepSeek V4 Flash</option>
            <option value="gemma3:4b">Gemma3 4B</option>
          </select>
        </div>
        <div class="form-group">
          <label>知识库 (DDL)</label>
          <select :value="node.data.config.knowledgeBaseId" @change="updateConfig('knowledgeBaseId', ($event.target as HTMLSelectElement).value)">
            <option value="">请选择 DDL 知识库</option>
            <option v-for="kb in ddlKnowledgeBases" :key="kb.id" :value="String(kb.id)">{{ kb.name }}</option>
          </select>
        </div>
        <div class="form-group">
          <label>数据库名</label>
          <input :value="node.data.config.databaseName" @input="updateConfig('databaseName', ($event.target as HTMLInputElement).value)" />
          <p class="hint">留空使用默认数据库</p>
        </div>
        <div class="form-group">
          <label>输入键</label>
          <input :value="node.data.config.inputKey" @input="updateConfig('inputKey', ($event.target as HTMLInputElement).value)" />
        </div>
        <div class="form-group">
          <label>输出键</label>
          <input :value="node.data.config.outputKey" @input="updateConfig('outputKey', ($event.target as HTMLInputElement).value)" />
        </div>
      </template>

      <template v-if="node.type === 'condition'">
        <div class="form-group">
          <label>输入键</label>
          <input :value="node.data.config.inputKey" @input="updateConfig('inputKey', ($event.target as HTMLInputElement).value)" />
          <p class="hint">读取上游节点的哪个输出值做判断</p>
        </div>
        <div class="form-group">
          <label>分支条件</label>
          <div class="condition-list">
            <div v-for="(cond, idx) in node.data.config.conditions" :key="idx" class="condition-row">
              <input class="cond-label" :value="cond.label" placeholder="分支名称"
                @input="updateCondition(idx, 'label', ($event.target as HTMLInputElement).value)" />
              <input class="cond-match" :value="cond.match === '__default__' ? '' : cond.match" placeholder="匹配关键词"
                @input="updateCondition(idx, 'match', ($event.target as HTMLInputElement).value)" />
              <button v-if="(node.data.config.conditions || []).length > 1" class="cond-remove" @click="removeCondition(idx)">×</button>
            </div>
          </div>
          <button class="btn-add" @click="addCondition">+ 添加分支</button>
          <p class="hint">关键词留空 = 默认分支</p>
        </div>
      </template>

      <template v-if="node.type === 'tool'">
        <div class="form-group">
          <label>请求方式</label>
          <select :value="node.data.config.method" @change="updateConfig('method', ($event.target as HTMLSelectElement).value)">
            <option value="GET">GET</option>
            <option value="POST">POST</option>
            <option value="PUT">PUT</option>
            <option value="DELETE">DELETE</option>
          </select>
        </div>
        <div class="form-group">
          <label>URL</label>
          <input :value="node.data.config.url" @input="updateConfig('url', ($event.target as HTMLInputElement).value)" placeholder="https://api.example.com/data" />
        </div>
        <div class="form-group">
          <label>请求头 (JSON)</label>
          <textarea rows="2" :value="headersJson" @input="updateHeaders(($event.target as HTMLTextAreaElement).value)" placeholder='{"Authorization": "Bearer xxx"}' />
        </div>
        <div class="form-group">
          <label>请求体模板</label>
          <textarea rows="3" :value="node.data.config.bodyTemplate"
            @input="updateConfig('bodyTemplate', ($event.target as HTMLTextAreaElement).value)" />
          <p class="hint">使用 &#123;&#123;input&#125;&#125; 作为输入变量占位符</p>
        </div>
        <div class="form-group">
          <label>输入键</label>
          <input :value="node.data.config.inputKey" @input="updateConfig('inputKey', ($event.target as HTMLInputElement).value)" />
        </div>
        <div class="form-group">
          <label>输出键</label>
          <input :value="node.data.config.outputKey" @input="updateConfig('outputKey', ($event.target as HTMLInputElement).value)" />
        </div>
      </template>

      <template v-if="node.type === 'subworkflow'">
        <div class="form-group">
          <label>目标工作流 ID</label>
          <input :value="node.data.config.workflowId" @input="updateConfig('workflowId', ($event.target as HTMLInputElement).value)" placeholder="输入工作流 ID" />
          <p class="hint">填入已创建的工作流 ID，执行时将调用该工作流</p>
        </div>
        <div class="form-group">
          <label>输入键</label>
          <input :value="node.data.config.inputKey" @input="updateConfig('inputKey', ($event.target as HTMLInputElement).value)" />
        </div>
        <div class="form-group">
          <label>输出键</label>
          <input :value="node.data.config.outputKey" @input="updateConfig('outputKey', ($event.target as HTMLInputElement).value)" />
        </div>
      </template>

      <template v-if="node.type === 'loop'">
        <div class="form-group">
          <label>最大迭代次数</label>
          <input type="number" :value="node.data.config.maxIterations" @input="updateConfig('maxIterations', parseInt(($event.target as HTMLInputElement).value) || 5)" />
        </div>
        <div class="form-group">
          <label>退出条件</label>
          <input :value="node.data.config.exitCondition" @input="updateConfig('exitCondition', ($event.target as HTMLInputElement).value)" placeholder="包含此文本时退出" />
          <p class="hint">当输入值包含指定文本时停止循环</p>
        </div>
        <div class="form-group">
          <label>输入键</label>
          <input :value="node.data.config.inputKey" @input="updateConfig('inputKey', ($event.target as HTMLInputElement).value)" />
        </div>
        <div class="form-group">
          <label>输出键</label>
          <input :value="node.data.config.outputKey" @input="updateConfig('outputKey', ($event.target as HTMLInputElement).value)" />
        </div>
      </template>

      <template v-if="node.type === 'end'">
        <div class="form-group">
          <label>回复要求</label>
          <textarea v-model="replyText" rows="4" placeholder="例如：以简洁的中文回答用户问题，包含关键数据和结论" />
          <p class="hint">所有节点执行完成后，系统将根据此要求用 LLM 生成最终对话回复</p>
        </div>
      </template>

      <button v-if="node.type !== 'start' && node.type !== 'end'" class="btn-delete" @click="handleDeleteNode">删除此节点</button>
    </template>

    <div v-else class="empty-hint">
      <p>点击画布中的节点查看配置</p>
      <p class="hint">选中节点/连线后按 Delete 键删除</p>
    </div>
  </div>
</template>

<style scoped lang="scss">
.config-panel {
  width: 260px; padding: 16px; border-left: 1px solid var(--border-subtle);
  background: var(--bg-surface); overflow-y: auto;
  h4 { font-size: 12px; font-weight: 600; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.06em; margin-bottom: 16px; }
}
.form-group {
  margin-bottom: 14px;
  label { display: block; font-size: 12px; font-weight: 500; color: var(--text-secondary); margin-bottom: 4px; }
  input, select, textarea { width: 100%; padding: 6px 10px; border: 1px solid var(--border-subtle); border-radius: 6px; font-size: 13px; background: var(--bg-primary); color: var(--text-primary); }
}
.hint { font-size: 11px; color: var(--text-muted); margin-top: 4px; }
.btn-delete {
  margin-top: 20px;
  width: 100%;
  padding: 8px;
  border: 1px solid #fca5a5;
  border-radius: 6px;
  background: #fef2f2;
  color: #dc2626;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
  &:hover { background: #fee2e2; }
}
.empty-hint { color: var(--text-muted); font-size: 13px; text-align: center; padding-top: 40px; }
.condition-list { display: flex; flex-direction: column; gap: 6px; }
.condition-row {
  display: flex; gap: 4px; align-items: center;
  .cond-label { width: 90px; }
  .cond-match { flex: 1; }
  input { padding: 5px 8px; border: 1px solid var(--border-subtle); border-radius: 4px; font-size: 12px; background: var(--bg-primary); color: var(--text-primary); }
}
.cond-remove { width: 22px; height: 22px; border: none; border-radius: 4px; background: #fee2e2; color: #dc2626; cursor: pointer; font-size: 14px; display: flex; align-items: center; justify-content: center; }
.btn-add { margin-top: 6px; width: 100%; padding: 6px; border: 1px dashed var(--border-subtle); border-radius: 6px; background: transparent; color: var(--text-secondary); font-size: 12px; cursor: pointer; &:hover { border-color: var(--text-secondary); } }
</style>
