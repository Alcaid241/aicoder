<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { modelApi } from '@/api/model'
import type { ModelProviderDTO, ModelConfigDTO, CreateModelConfigRequest } from '@/types'

const configs = ref<ModelConfigDTO[]>([])
const providers = ref<ModelProviderDTO[]>([])
const loading = ref(false)
const showModal = ref(false)
const editingId = ref<number | null>(null)

const defaultForm: CreateModelConfigRequest = {
  providerId: 0,
  displayName: '',
  modelCode: '',
  modelType: 'CHAT',
  enabled: 1,
  sort: 0
}

const form = ref<CreateModelConfigRequest>({ ...defaultForm })

const fetchList = async () => {
  loading.value = true
  try {
    const res = await modelApi.configList()
    configs.value = res.data
  } catch {
    // ignore
  } finally {
    loading.value = false
  }
}

const fetchProviders = async () => {
  try {
    const res = await modelApi.providerList()
    providers.value = res.data
  } catch {
    // ignore
  }
}

onMounted(() => {
  fetchList()
  fetchProviders()
})

const openCreateModal = () => {
  editingId.value = null
  form.value = { ...defaultForm, providerId: providers.value.length > 0 ? providers.value[0].id : 0 }
  showModal.value = true
}

const openEditModal = (config: ModelConfigDTO) => {
  editingId.value = config.id
  form.value = {
    providerId: config.providerId,
    displayName: config.displayName,
    modelCode: config.modelCode,
    modelType: config.modelType,
    enabled: config.enabled,
    sort: config.sort
  }
  showModal.value = true
}

const handleSave = async () => {
  try {
    if (editingId.value) {
      await modelApi.configUpdate(editingId.value, form.value)
    } else {
      await modelApi.configCreate(form.value)
    }
    showModal.value = false
    await fetchList()
  } catch {
    // ignore
  }
}

const handleDelete = async (id: number) => {
  if (!confirm('确定删除此模型配置？')) return
  try {
    await modelApi.configDelete(id)
    await fetchList()
  } catch {
    // ignore
  }
}

const modelTypeLabel = (type: string) => {
  switch (type) {
    case 'CHAT': return '对话'
    case 'EMBEDDING': return '嵌入'
    default: return type
  }
}
</script>

<template>
  <div class="model-config-view">
    <div class="page-header">
      <h2>模型配置</h2>
      <button class="btn btn-primary" @click="openCreateModal">+ 新增模型</button>
    </div>

    <div v-if="loading" class="loading">加载中...</div>

    <div v-else class="table-container card">
      <table>
        <thead>
          <tr>
            <th>模型名称</th>
            <th>模型编码</th>
            <th>所属厂商</th>
            <th>模型类型</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="config in configs" :key="config.id">
            <td>{{ config.displayName }}</td>
            <td><span class="tag tag-blue">{{ config.modelCode }}</span></td>
            <td>{{ config.providerName }}</td>
            <td>
              <span class="tag" :class="config.modelType === 'CHAT' ? 'tag-purple' : 'tag-orange'">
                {{ modelTypeLabel(config.modelType) }}
              </span>
            </td>
            <td>
              <span class="tag" :class="config.enabled === 1 ? 'tag-green' : 'tag-gray'">
                {{ config.enabled === 1 ? '启用' : '禁用' }}
              </span>
            </td>
            <td>
              <div class="action-btns">
                <button class="btn btn-secondary btn-sm" @click="openEditModal(config)">编辑</button>
                <button class="btn btn-danger btn-sm" @click="handleDelete(config.id)">删除</button>
              </div>
            </td>
          </tr>
          <tr v-if="configs.length === 0">
            <td colspan="6" class="empty-row">暂无模型配置</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="showModal" class="modal-overlay" @click.self="showModal = false">
      <div class="modal-content card">
        <h3>{{ editingId ? '编辑模型' : '新增模型' }}</h3>
        <div class="form-group">
          <label>所属厂商 <span class="required">*</span></label>
          <select v-model.number="form.providerId">
            <option v-for="p in providers" :key="p.id" :value="p.id">{{ p.name }}</option>
          </select>
        </div>
        <div class="form-group">
          <label>模型名称 <span class="required">*</span></label>
          <input v-model="form.displayName" placeholder="如 Gemma 3 4B" />
        </div>
        <div class="form-group">
          <label>模型编码 <span class="required">*</span></label>
          <input v-model="form.modelCode" placeholder="如 gemma3:4b" />
        </div>
        <div class="form-group">
          <label>模型类型 <span class="required">*</span></label>
          <select v-model="form.modelType">
            <option value="CHAT">对话 (CHAT)</option>
            <option value="EMBEDDING">嵌入 (EMBEDDING)</option>
          </select>
        </div>
        <div class="form-row">
          <div class="form-group">
            <label>状态</label>
            <select v-model.number="form.enabled">
              <option :value="1">启用</option>
              <option :value="0">禁用</option>
            </select>
          </div>
          <div class="form-group">
            <label>排序</label>
            <input v-model.number="form.sort" type="number" placeholder="0" />
          </div>
        </div>
        <div class="form-group">
          <label>支持 Function Calling</label>
          <select v-model.number="form.supportTools">
            <option :value="1">是</option>
            <option :value="0">否</option>
          </select>
          <p class="form-hint">开启后对话模式可调用技能工具（read_skill/submit_skill_draft）</p>
        </div>
        <div class="modal-actions">
          <button class="btn btn-secondary" @click="showModal = false">取消</button>
          <button class="btn btn-primary" @click="handleSave">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.model-config-view { height: 100%; }

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
  h2 {
    font-family: 'Sora', sans-serif;
    font-size: 18px;
    font-weight: 600;
    color: var(--text-primary);
  }
}

.loading { text-align: center; padding: 60px; color: var(--text-muted); }

.table-container {
  overflow-x: auto;
  padding: 0;

  table {
    width: 100%;
    border-collapse: collapse;
    th, td {
      padding: 14px 16px;
      text-align: left;
      border-bottom: 1px solid var(--border-default);
      font-size: 14px;
    }
    th {
      background: var(--bg-overlay);
      font-weight: 600;
      color: var(--text-secondary);
      font-family: 'Sora', sans-serif;
      font-size: 12px;
      letter-spacing: 0.02em;
    }
    td { color: var(--text-primary); }
  }
}

.empty-row {
  text-align: center !important;
  color: var(--text-muted) !important;
  padding: 40px !important;
}

.action-btns {
  display: flex;
  gap: 6px;
}

.required { color: #e74c3c; }
</style>
