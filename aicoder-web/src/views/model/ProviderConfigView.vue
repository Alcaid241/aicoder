<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { modelApi } from '@/api/model'
import type { ModelProviderDTO, CreateProviderRequest } from '@/types'

const providers = ref<ModelProviderDTO[]>([])
const loading = ref(false)
const showModal = ref(false)
const editingId = ref<number | null>(null)

const defaultForm: CreateProviderRequest = {
  name: '',
  code: '',
  baseUrl: 'http://localhost:11434',
  enabled: 1,
  sort: 0
}

const form = ref<CreateProviderRequest>({ ...defaultForm })

const fetchList = async () => {
  loading.value = true
  try {
    const res = await modelApi.providerList()
    providers.value = res.data
  } catch {
    // ignore
  } finally {
    loading.value = false
  }
}

onMounted(fetchList)

const openCreateModal = () => {
  editingId.value = null
  form.value = { ...defaultForm }
  showModal.value = true
}

const openEditModal = (provider: ModelProviderDTO) => {
  editingId.value = provider.id
  form.value = {
    name: provider.name,
    logo: provider.logo,
    code: provider.code,
    baseUrl: provider.baseUrl,
    apiKey: provider.apiKey,
    description: provider.description,
    enabled: provider.enabled,
    sort: provider.sort
  }
  showModal.value = true
}

const handleSave = async () => {
  try {
    if (editingId.value) {
      await modelApi.providerUpdate(editingId.value, form.value)
    } else {
      await modelApi.providerCreate(form.value)
    }
    showModal.value = false
    await fetchList()
  } catch {
    // ignore
  }
}

const handleDelete = async (id: number) => {
  if (!confirm('确定删除此厂商？删除后关联的模型配置可能受到影响。')) return
  try {
    await modelApi.providerDelete(id)
    await fetchList()
  } catch {
    // ignore
  }
}

const maskApiKey = (key: string) => {
  if (!key) return '—'
  if (key.length > 8) return key.substring(0, 8) + '***'
  return '***'
}
</script>

<template>
  <div class="provider-view">
    <div class="page-header">
      <h2>厂商配置</h2>
      <button class="btn btn-primary" @click="openCreateModal">+ 新增厂商</button>
    </div>

    <div v-if="loading" class="loading">加载中...</div>

    <div v-else class="table-container card">
      <table>
        <thead>
          <tr>
            <th>厂商名称</th>
            <th>编码</th>
            <th>Base URL</th>
            <th>API Key</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="provider in providers" :key="provider.id">
            <td>{{ provider.name }}</td>
            <td><span class="tag tag-blue">{{ provider.code }}</span></td>
            <td>{{ provider.baseUrl }}</td>
            <td class="api-key-col">{{ maskApiKey(provider.apiKey) }}</td>
            <td>
              <span class="tag" :class="provider.enabled === 1 ? 'tag-green' : 'tag-gray'">
                {{ provider.enabled === 1 ? '启用' : '禁用' }}
              </span>
            </td>
            <td>
              <div class="action-btns">
                <button class="btn btn-secondary btn-sm" @click="openEditModal(provider)">编辑</button>
                <button class="btn btn-danger btn-sm" @click="handleDelete(provider.id)">删除</button>
              </div>
            </td>
          </tr>
          <tr v-if="providers.length === 0">
            <td colspan="6" class="empty-row">暂无厂商配置</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="showModal" class="modal-overlay" @click.self="showModal = false">
      <div class="modal-content card">
        <h3>{{ editingId ? '编辑厂商' : '新增厂商' }}</h3>
        <div class="form-group">
          <label>厂商名称 <span class="required">*</span></label>
          <input v-model="form.name" placeholder="如 Ollama、DeepSeek" />
        </div>
        <div class="form-group">
          <label>编码 <span class="required">*</span></label>
          <input v-model="form.code" :disabled="!!editingId" placeholder="如 ollama、deepseek" />
        </div>
        <div class="form-group">
          <label>Base URL</label>
          <input v-model="form.baseUrl" placeholder="默认 http://localhost:11434" />
        </div>
        <div class="form-group">
          <label>Logo URL</label>
          <input v-model="form.logo" placeholder="Logo 图片地址（可选）" />
        </div>
        <div class="form-group">
          <label>API Key</label>
          <input v-model="form.apiKey" type="password" placeholder="API Key（可选）" />
        </div>
        <div class="form-group">
          <label>描述</label>
          <textarea v-model="form.description" rows="2" placeholder="厂商描述（可选）" />
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
        <div class="modal-actions">
          <button class="btn btn-secondary" @click="showModal = false">取消</button>
          <button class="btn btn-primary" @click="handleSave">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.provider-view { height: 100%; }

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

.api-key-col {
  font-family: monospace;
  font-size: 13px;
  color: var(--text-muted);
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
