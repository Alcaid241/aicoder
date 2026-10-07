<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { vectorDbApi } from '@/api/vectorDb'
import type { VectorDbConfig, VectorDbConfigDTO } from '@/types'

const configs = ref<VectorDbConfig[]>([])
const loading = ref(false)
const showModal = ref(false)
const editingId = ref<number | null>(null)

const form = ref<VectorDbConfigDTO>({
  dbType: 'CHROMA',
  host: 'localhost',
  port: 8000,
  databaseName: '',
  collectionName: 'aicoder_collection',
  description: ''
})

const dbTypeDefaults: Record<string, { host: string; port: number; databaseName: string; collectionName: string }> = {
  REDIS: { host: 'localhost', port: 6379, databaseName: '', collectionName: 'aicoder_vector' },
  CHROMA: { host: 'localhost', port: 8000, databaseName: '', collectionName: 'aicoder_collection' },
  MILVUS: { host: 'localhost', port: 19530, databaseName: 'default', collectionName: 'aicoder_collection' }
}

const onDbTypeChange = () => {
  const defaults = dbTypeDefaults[form.value.dbType]
  if (defaults) {
    form.value.host = defaults.host
    form.value.port = defaults.port
    form.value.databaseName = defaults.databaseName
    form.value.collectionName = defaults.collectionName
  }
}

const fetchList = async () => {
  loading.value = true
  try {
    const res = await vectorDbApi.list()
    configs.value = res.data
  } catch {
    // ignore
  } finally {
    loading.value = false
  }
}

onMounted(fetchList)

const openCreateModal = () => {
  editingId.value = null
  const defaults = dbTypeDefaults['CHROMA']
  form.value = { dbType: 'CHROMA', ...defaults, description: '' }
  showModal.value = true
}

const openEditModal = (config: VectorDbConfig) => {
  editingId.value = config.id
  form.value = {
    dbType: config.dbType,
    host: config.host,
    port: config.port,
    databaseName: config.databaseName,
    collectionName: config.collectionName,
    description: config.description
  }
  showModal.value = true
}

const handleSave = async () => {
  try {
    if (editingId.value) {
      await vectorDbApi.update(editingId.value, form.value)
    } else {
      await vectorDbApi.create(form.value)
    }
    showModal.value = false
    await fetchList()
  } catch {
    // ignore
  }
}

const handleActivate = async (id: number) => {
  try {
    await vectorDbApi.activate(id)
    await fetchList()
  } catch {
    // ignore
  }
}

const handleDelete = async (id: number) => {
  if (!confirm('确定删除此配置？')) return
  try {
    await vectorDbApi.delete(id)
    await fetchList()
  } catch {
    // ignore
  }
}

const dbTypeLabel = (type: string) => {
  switch (type) {
    case 'REDIS': return 'Redis'
    case 'CHROMA': return 'Chroma'
    case 'MILVUS': return 'Milvus'
    default: return type
  }
}
</script>

<template>
  <div class="vectordb-view">
    <div class="page-header">
      <h2>向量库配置</h2>
      <button class="btn btn-primary" @click="openCreateModal">+ 新增配置</button>
    </div>

    <div v-if="loading" class="loading">加载中...</div>

    <div v-else class="table-container card">
      <table>
        <thead>
          <tr>
            <th>类型</th>
            <th>Host</th>
            <th>Port</th>
            <th>集合名</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="config in configs" :key="config.id">
            <td><span class="tag tag-blue">{{ dbTypeLabel(config.dbType) }}</span></td>
            <td>{{ config.host }}</td>
            <td>{{ config.port }}</td>
            <td>{{ config.collectionName }}</td>
            <td>
              <span class="tag" :class="config.active ? 'tag-green' : 'tag-gray'">
                {{ config.active ? '已激活' : '未激活' }}
              </span>
            </td>
            <td>
              <div class="action-btns">
                <button class="btn btn-secondary btn-sm" @click="openEditModal(config)">编辑</button>
                <button
                  v-if="!config.active"
                  class="btn btn-success btn-sm"
                  @click="handleActivate(config.id)"
                >激活</button>
                <button class="btn btn-danger btn-sm" @click="handleDelete(config.id)">删除</button>
              </div>
            </td>
          </tr>
          <tr v-if="configs.length === 0">
            <td colspan="6" class="empty-row">暂无配置</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="showModal" class="modal-overlay" @click.self="showModal = false">
      <div class="modal-content card">
        <h3>{{ editingId ? '编辑配置' : '新增配置' }}</h3>
        <div class="form-group">
          <label>类型</label>
          <select v-model="form.dbType" @change="onDbTypeChange">
            <option value="REDIS">Redis</option>
            <option value="CHROMA">Chroma</option>
            <option value="MILVUS">Milvus</option>
          </select>
        </div>
        <div class="form-row">
          <div class="form-group">
            <label>Host</label>
            <input v-model="form.host" placeholder="如 localhost" />
          </div>
          <div class="form-group">
            <label>Port</label>
            <input v-model.number="form.port" type="number" placeholder="端口号" />
          </div>
        </div>
        <div class="form-group">
          <label>数据库名</label>
          <input v-model="form.databaseName" placeholder="数据库名（可选）" />
        </div>
        <div class="form-group">
          <label>集合名</label>
          <input v-model="form.collectionName" placeholder="集合名/索引名" />
        </div>
        <div class="form-group">
          <label>描述</label>
          <textarea v-model="form.description" rows="2" placeholder="配置描述（可选）" />
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
.vectordb-view { height: 100%; }

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
</style>
