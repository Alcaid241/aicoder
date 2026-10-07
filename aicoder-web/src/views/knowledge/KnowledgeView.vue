<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { knowledgeApi, type KnowledgeDocument } from '@/api/knowledge'
import { vectorDbApi } from '@/api/vectorDb'
import type { KnowledgeBase, KnowledgeBaseCreateRequest, KnowledgeBaseUpdateRequest, VectorDbConfig } from '@/types'

const router = useRouter()
const knowledgeBases = ref<KnowledgeBase[]>([])
const loading = ref(false)
const errorMsg = ref('')

const searchKeyword = ref('')
const filteredKBs = computed(() => {
  if (!searchKeyword.value) return knowledgeBases.value
  const kw = searchKeyword.value.toLowerCase()
  return knowledgeBases.value.filter(kb =>
    kb.name.toLowerCase().includes(kw) ||
    (kb.description || '').toLowerCase().includes(kw)
  )
})

const selectedKbId = ref<number | null>(null)
const selectedKb = computed(() =>
  knowledgeBases.value.find(k => k.id === selectedKbId.value) || null
)
const selectedKbDocuments = ref<KnowledgeDocument[]>([])
const docsLoading = ref(false)

const editMode = ref(false)
const editForm = ref<KnowledgeBaseUpdateRequest>({
  name: '', type: 'KB', description: '', systemPrompt: '',
  databaseName: '', jdbcUrl: '', dbUsername: '', dbPassword: ''
})

const showCreateModal = ref(false)
const createForm = ref<KnowledgeBaseCreateRequest>({
  name: '', type: 'KB', description: '', systemPrompt: '',
  databaseName: '', jdbcUrl: '', dbUsername: '', dbPassword: '',
  vectorDbConfigId: undefined
})

const vectorDbConfigs = ref<VectorDbConfig[]>([])

const showUploadModal = ref(false)
const uploadKbId = ref<number | null>(null)
const uploadFile = ref<File | null>(null)
const uploading = ref(false)
const uploadError = ref('')

const fetchList = async () => {
  loading.value = true
  try {
    const res = await knowledgeApi.list()
    knowledgeBases.value = res.data
  } catch { /* ignore */ }
  finally { loading.value = false }
}

onMounted(fetchList)

const selectKb = async (id: number) => {
  selectedKbId.value = id
  editMode.value = false
  docsLoading.value = true
  try {
    const res = await knowledgeApi.getDocuments(id)
    selectedKbDocuments.value = res.data as KnowledgeDocument[]
  } catch { selectedKbDocuments.value = [] }
  finally { docsLoading.value = false }
}

const enterEditMode = () => {
  if (!selectedKb.value) return
  editForm.value = {
    name: selectedKb.value.name,
    type: selectedKb.value.type,
    description: selectedKb.value.description || '',
    systemPrompt: selectedKb.value.systemPrompt || '',
    databaseName: selectedKb.value.databaseName || '',
    jdbcUrl: selectedKb.value.jdbcUrl || '',
    dbUsername: selectedKb.value.dbUsername || '',
    dbPassword: selectedKb.value.dbPassword || ''
  }
  editMode.value = true
}

const cancelEdit = () => { editMode.value = false }

const handleUpdate = async () => {
  if (!editForm.value.name || !selectedKbId.value) return
  errorMsg.value = ''
  try {
    await knowledgeApi.update(selectedKbId.value, editForm.value)
    editMode.value = false
    await fetchList()
  } catch (e: any) {
    errorMsg.value = e.response?.data?.message || '更新失败'
  }
}

const openCreateModal = async () => {
  errorMsg.value = ''
  createForm.value = { name: '', type: 'KB', description: '', systemPrompt: '', databaseName: '', jdbcUrl: '', dbUsername: '', dbPassword: '', vectorDbConfigId: undefined }
  try {
    const res = await vectorDbApi.list()
    vectorDbConfigs.value = res.data
  } catch { vectorDbConfigs.value = [] }
  showCreateModal.value = true
}

const handleCreate = async () => {
  if (!createForm.value.name) return
  errorMsg.value = ''
  try {
    await knowledgeApi.create(createForm.value)
    showCreateModal.value = false
    await fetchList()
  } catch (e: any) {
    const msg = e.response?.data?.error || e.response?.data?.message || '创建失败'
    if (msg.includes('没有激活的向量数据库')) {
      errorMsg.value = '没有激活的向量数据库配置，请先前往'
    } else {
      errorMsg.value = msg
    }
  }
}

const goToVectorDbConfig = () => { router.push('/vectordb') }

const handleDelete = async (id: number) => {
  if (!confirm('确定删除此知识库？')) return
  try {
    await knowledgeApi.delete(id)
    if (selectedKbId.value === id) {
      selectedKbId.value = null
      selectedKbDocuments.value = []
    }
    await fetchList()
  } catch { /* ignore */ }
}

const openUploadInDetail = () => {
  if (!selectedKbId.value) return
  uploadKbId.value = selectedKbId.value
  uploadFile.value = null
  uploadError.value = ''
  showUploadModal.value = true
}

const handleDeleteDoc = async (doc: KnowledgeDocument) => {
  if (!confirm(`确定删除文档「${doc.fileName}」？`)) return
  if (!selectedKbId.value) return
  try {
    await knowledgeApi.deleteDocument(selectedKbId.value, doc.id)
    const res = await knowledgeApi.getDocuments(selectedKbId.value)
    selectedKbDocuments.value = res.data as KnowledgeDocument[]
    await fetchList()
  } catch { /* ignore */ }
}

const handleDownloadDoc = async (doc: KnowledgeDocument) => {
  if (!selectedKbId.value) return
  try {
    const resp = await knowledgeApi.downloadDocument(selectedKbId.value, doc.id)
    if (!resp.ok) {
      const data = await resp.json()
      alert(data.error || '下载失败')
      return
    }
    const blob = await resp.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = doc.fileName
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  } catch {
    alert('下载失败')
  }
}

const handleFileChange = (e: Event) => {
  const target = e.target as HTMLInputElement
  if (target.files && target.files[0]) uploadFile.value = target.files[0]
}

const handleUpload = async () => {
  if (!uploadKbId.value || !uploadFile.value) return
  uploading.value = true
  uploadError.value = ''
  try {
    await knowledgeApi.upload(uploadKbId.value, uploadFile.value)
    showUploadModal.value = false
    await fetchList()
    if (selectedKbId.value === uploadKbId.value) {
      const res = await knowledgeApi.getDocuments(uploadKbId.value)
      selectedKbDocuments.value = res.data as KnowledgeDocument[]
    }
  } catch (e: any) {
    uploadError.value = e.response?.data?.message || '上传失败'
  } finally { uploading.value = false }
}

const formatFileSize = (bytes: number): string => {
  if (!bytes) return '0 B'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

const docStatusClass = (status: string) => {
  switch (status) {
    case 'COMPLETED': return 'tag-green'
    case 'PROCESSING': return 'tag-yellow'
    case 'ERROR': return 'tag-red'
    default: return 'tag-gray'
  }
}

const docStatusLabel = (status: string) => {
  switch (status) {
    case 'COMPLETED': return '就绪'
    case 'PROCESSING': return '处理中'
    case 'ERROR': return '异常'
    default: return status
  }
}

const statusLabel = (status: number) => {
  switch (status) {
    case 0: return { text: '处理中', cls: 'tag-yellow' }
    case 1: return { text: '就绪', cls: 'tag-green' }
    case 2: return { text: '异常', cls: 'tag-red' }
    default: return { text: '未知', cls: 'tag-gray' }
  }
}

const vectorDbLabel = (dbType: string) => {
  switch (dbType?.toUpperCase()) {
    case 'CHROMA': return { text: 'Chroma', cls: 'tag-teal' }
    case 'MILVUS': return { text: 'Milvus', cls: 'tag-purple' }
    case 'REDIS': return { text: 'Redis', cls: 'tag-red' }
    default: return { text: dbType || '未配置', cls: 'tag-gray' }
  }
}
</script>

<template>
  <div class="knowledge-view">
    <div class="page-topbar">
      <div>
        <h2>知识库</h2>
        <span class="count-tag">{{ knowledgeBases.length }} 个</span>
      </div>
      <button class="btn btn-primary" @click="openCreateModal">
        <svg width="14" height="14" viewBox="0 0 14 14"><line x1="7" y1="2" x2="7" y2="12" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/><line x1="2" y1="7" x2="12" y2="7" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>
        新建知识库
      </button>
    </div>

    <div v-if="loading" class="empty-state">加载中...</div>

    <div v-else class="kb-layout">
      <!-- 左侧列表 -->
      <aside class="kb-sidebar">
        <input v-model="searchKeyword" class="kb-search" placeholder="搜索知识库..." />
        <div class="kb-list">
          <div
            v-for="kb in filteredKBs"
            :key="kb.id"
            class="kb-list-item"
            :class="{ active: selectedKbId === kb.id }"
            @click="selectKb(kb.id)"
          >
            <div class="kb-item-head">
              <span class="kb-item-name">{{ kb.name }}</span>
              <span class="tag" :class="kb.type === 'DDL' ? 'tag-blue' : 'tag-accent'">{{ kb.type }}</span>
            </div>
            <div class="kb-item-meta">
              <span>文档 {{ kb.documentCount }}</span>
              <span class="tag" :class="statusLabel(kb.status).cls">{{ statusLabel(kb.status).text }}</span>
            </div>
          </div>
          <div v-if="filteredKBs.length === 0" class="empty-state">暂无知识库</div>
        </div>
      </aside>

      <!-- 右侧详情 -->
      <main class="kb-detail">
        <div v-if="!selectedKb" class="detail-placeholder">
          <svg width="40" height="40" viewBox="0 0 24 24" style="color: var(--text-muted)">
            <rect x="3" y="3" width="7" height="7" rx="1" fill="none" stroke="currentColor" stroke-width="1.4"/>
            <rect x="14" y="3" width="7" height="7" rx="1" fill="none" stroke="currentColor" stroke-width="1.4"/>
            <rect x="3" y="14" width="7" height="7" rx="1" fill="none" stroke="currentColor" stroke-width="1.4"/>
            <rect x="14" y="14" width="7" height="7" rx="1" fill="none" stroke="currentColor" stroke-width="1.4"/>
          </svg>
          <span>选择左侧知识库查看详情</span>
        </div>

        <template v-if="selectedKb">
          <!-- 工具栏 -->
          <div class="detail-toolbar">
            <div class="detail-tabs">
              <button class="tab-btn" :class="{ active: editMode }" @click="enterEditMode">编辑</button>
              <button class="tab-btn" :class="{ active: !editMode }" @click="editMode = false">详情</button>
            </div>
            <div class="toolbar-right">
              <button class="btn btn-ghost btn-sm" style="color: var(--danger)" @click="handleDelete(selectedKb.id)">删除</button>
            </div>
          </div>

          <!-- 查看模式 -->
          <div v-if="!editMode" class="detail-view">
            <!-- 信息双栏 -->
            <div class="info-columns">
              <!-- 左列：基础信息 -->
              <div class="info-col">
                <div class="spec-card">
                  <div class="spec-card-header">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253"/></svg>
                    基本信息
                  </div>
                  <div class="spec-rows">
                    <div class="spec-row">
                      <span class="spec-label">名称</span>
                      <span class="spec-value spec-value-name">{{ selectedKb.name }}</span>
                    </div>
                    <div class="spec-row">
                      <span class="spec-label">类型</span>
                      <span class="spec-value"><span class="tag" :class="selectedKb.type === 'DDL' ? 'tag-blue' : 'tag-accent'">{{ selectedKb.type === 'DDL' ? 'DDL' : '知识库' }}</span></span>
                    </div>
                    <div class="spec-row">
                      <span class="spec-label">描述</span>
                      <span class="spec-value">{{ selectedKb.description || '—' }}</span>
                    </div>
                    <div v-if="selectedKb.type === 'KB'" class="spec-row spec-row-block">
                      <span class="spec-label">提示词</span>
                      <span class="spec-value spec-value-pre">{{ selectedKb.systemPrompt || '未设置' }}</span>
                    </div>
                  </div>
                </div>

                <div v-if="selectedKb.type === 'DDL'" class="spec-card">
                  <div class="spec-card-header">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><ellipse cx="12" cy="5" rx="9" ry="3"/><path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3"/><path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5"/></svg>
                    数据库连接
                  </div>
                  <div class="spec-rows">
                    <div class="spec-row">
                      <span class="spec-label">数据库名</span>
                      <span class="spec-value">{{ selectedKb.databaseName || '未设置' }}</span>
                    </div>
                    <div class="spec-row">
                      <span class="spec-label">JDBC URL</span>
                      <span class="spec-value spec-mono">{{ selectedKb.jdbcUrl || '系统默认' }}</span>
                    </div>
                    <div class="spec-row">
                      <span class="spec-label">用户名</span>
                      <span class="spec-value">{{ selectedKb.dbUsername || '未设置' }}</span>
                    </div>
                    <div class="spec-row">
                      <span class="spec-label">密码</span>
                      <span class="spec-value">{{ selectedKb.dbPassword ? '••••••' : '未设置' }}</span>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 右列：向量库信息 -->
              <div class="info-col">
                <div class="spec-card spec-card-vector">
                  <div class="spec-card-header">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M4 7v10c0 2.21 3.582 4 8 4s8-1.79 8-4V7M4 7c0 2.21 3.582 4 8 4s8-1.79 8-4M4 7c0-2.21 3.582-4 8-4s8 1.79 8 4"/></svg>
                    向量库信息
                  </div>
                  <div class="spec-rows">
                    <div class="spec-row">
                      <span class="spec-label">类型</span>
                      <span class="spec-value"><span class="tag" :class="vectorDbLabel(selectedKb.vectorDbType).cls">{{ vectorDbLabel(selectedKb.vectorDbType).text }}</span></span>
                    </div>
                    <div class="spec-row">
                      <span class="spec-label">集合名</span>
                      <span class="spec-value spec-mono">{{ selectedKb.vectorCollection }}</span>
                    </div>
                    <div class="spec-row">
                      <span class="spec-label">状态</span>
                      <span class="spec-value"><span class="tag" :class="statusLabel(selectedKb.status).cls">{{ statusLabel(selectedKb.status).text }}</span></span>
                    </div>
                    <div class="spec-row">
                      <span class="spec-label">文档数量</span>
                      <span class="spec-value spec-value-num">{{ selectedKb.documentCount }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 文档列表 -->
            <div class="spec-card spec-card-docs">
              <div class="spec-card-header">
                <div class="docs-header-left">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>
                  文档列表
                </div>
                <button class="btn btn-ghost btn-sm" @click="openUploadInDetail">
                  <svg width="14" height="14" viewBox="0 0 14 14"><line x1="7" y1="2" x2="7" y2="12" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/><line x1="2" y1="7" x2="12" y2="7" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>
                  上传
                </button>
              </div>
              <table v-if="selectedKbDocuments.length" class="doc-table">
                <thead>
                  <tr>
                    <th>文件名</th>
                    <th>大小</th>
                    <th>分块数</th>
                    <th>状态</th>
                    <th>上传时间</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="doc in selectedKbDocuments" :key="doc.id">
                    <td class="doc-name-col">
                      <span class="doc-ext">{{ doc.fileType }}</span>
                      {{ doc.fileName }}
                    </td>
                    <td>{{ formatFileSize(doc.fileSize) }}</td>
                    <td>{{ doc.chunkCount }}</td>
                    <td><span class="tag" :class="docStatusClass(doc.status)">{{ docStatusLabel(doc.status) }}</span></td>
                    <td class="doc-time-col">{{ doc.createdAt?.substring(0, 16) }}</td>
                    <td class="doc-actions-col">
                      <button class="doc-action-btn" title="下载" @click="handleDownloadDoc(doc)">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
                      </button>
                      <button class="doc-action-btn doc-action-danger" title="删除" @click="handleDeleteDoc(doc)">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/></svg>
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
              <div v-else-if="docsLoading" class="docs-empty">加载中...</div>
              <div v-else class="docs-empty">暂无文档，点击上传</div>
            </div>
          </div>

          <!-- 编辑模式 -->
          <div v-else class="detail-edit">
            <div v-if="errorMsg" class="error-msg">
              {{ errorMsg }}
              <a v-if="errorMsg.includes('向量数据库')" href="javascript:void(0)" @click="goToVectorDbConfig">「向量库配置」</a>
              <template v-if="errorMsg.includes('向量数据库')">页面配置并激活</template>
            </div>

            <div class="info-columns">
              <!-- 左列：基础信息 -->
              <div class="info-col">
                <div class="edit-card">
                  <div class="spec-card-header">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253"/></svg>
                    基本信息
                  </div>
                  <div class="edit-fields">
                    <div class="edit-row">
                      <span class="edit-label">名称</span>
                      <div class="edit-control"><input v-model="editForm.name" placeholder="输入知识库名称" /></div>
                    </div>
                    <div class="edit-row">
                      <span class="edit-label">类型</span>
                      <div class="edit-control">
                        <select v-model="editForm.type">
                          <option value="KB">知识库</option>
                          <option value="DDL">DDL</option>
                        </select>
                      </div>
                    </div>
                    <div class="edit-row">
                      <span class="edit-label">描述</span>
                      <div class="edit-control"><textarea v-model="editForm.description" rows="2" placeholder="描述此知识库的用途" /></div>
                    </div>
                    <div v-if="editForm.type === 'KB'" class="edit-row">
                      <span class="edit-label">提示词</span>
                      <div class="edit-control"><textarea v-model="editForm.systemPrompt" rows="3" placeholder="自定义 AI 行为提示词" /></div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 右列：数据库连接（DDL）或留空 -->
              <div class="info-col">
                <div v-if="editForm.type === 'DDL'" class="edit-card">
                  <div class="spec-card-header">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><ellipse cx="12" cy="5" rx="9" ry="3"/><path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3"/><path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5"/></svg>
                    数据库连接
                  </div>
                  <div class="edit-fields">
                    <div class="edit-row">
                      <span class="edit-label">数据库名</span>
                      <div class="edit-control"><input v-model="editForm.databaseName" placeholder="例如：my_business_db" /></div>
                    </div>
                    <div class="edit-row">
                      <span class="edit-label">JDBC URL <span class="optional-tag">可选</span></span>
                      <div class="edit-control">
                        <input v-model="editForm.jdbcUrl" placeholder="jdbc:mysql://localhost:3306/db" />
                        <p class="hint">留空则使用系统默认数据库</p>
                      </div>
                    </div>
                    <div class="edit-row-pair">
                      <div class="edit-half">
                        <span class="edit-label">用户名</span>
                        <input v-model="editForm.dbUsername" placeholder="root" />
                      </div>
                      <div class="edit-half">
                        <span class="edit-label">密码</span>
                        <input v-model="editForm.dbPassword" type="password" placeholder="数据库密码" />
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div class="edit-actions">
              <button class="btn btn-secondary" @click="cancelEdit">取消</button>
              <button class="btn btn-primary" @click="handleUpdate" :disabled="!editForm.name">保存</button>
            </div>
          </div>
        </template>
      </main>
    </div>

    <!-- Create Modal -->
    <div v-if="showCreateModal" class="modal-overlay" @click.self="showCreateModal = false">
      <div class="modal-content">
        <h3>新建知识库</h3>
        <div class="form-group">
          <label>名称</label>
          <input v-model="createForm.name" placeholder="输入知识库名称" />
        </div>
        <div class="form-group">
          <label>类型</label>
          <select v-model="createForm.type">
            <option value="KB">知识库</option>
            <option value="DDL">DDL</option>
          </select>
        </div>
        <div class="form-group">
          <label>描述</label>
          <textarea v-model="createForm.description" rows="2" placeholder="描述此知识库的用途" />
        </div>
        <div class="form-group">
          <label>向量库 <span class="required-mark">*</span></label>
          <select v-model.number="createForm.vectorDbConfigId">
            <option :value="undefined" disabled>请选择向量数据库</option>
            <option v-for="cfg in vectorDbConfigs" :key="cfg.id" :value="cfg.id">
              {{ cfg.dbType.toUpperCase() }} — {{ cfg.host }}:{{ cfg.port }}{{ cfg.collectionName ? ' / ' + cfg.collectionName : '' }}{{ cfg.active ? '' : ' (未激活)' }}
            </option>
          </select>
          <p class="hint">选择用于存储文档向量的数据库，需先在「向量库配置」页面激活对应配置</p>
        </div>
        <div v-if="createForm.type === 'KB'" class="form-group">
          <label>System 提示词</label>
          <textarea v-model="createForm.systemPrompt" rows="2" placeholder="自定义 AI 行为提示词" />
        </div>
        <div v-if="createForm.type === 'DDL'" class="form-section">
          <p class="section-hint">填写目标数据库连接信息，用于后续 SQL 执行</p>
          <div class="form-group">
            <label>数据库名</label>
            <input v-model="createForm.databaseName" placeholder="例如：my_business_db" />
          </div>
          <div class="form-group">
            <label>JDBC URL <span class="optional-tag">可选</span></label>
            <input v-model="createForm.jdbcUrl" placeholder="jdbc:mysql://localhost:3306/my_business_db" />
            <p class="hint">留空则使用系统默认数据库</p>
          </div>
          <div class="form-row">
            <div class="form-group form-half">
              <label>用户名</label>
              <input v-model="createForm.dbUsername" placeholder="root" />
            </div>
            <div class="form-group form-half">
              <label>密码</label>
              <input v-model="createForm.dbPassword" type="password" placeholder="数据库密码" />
            </div>
          </div>
        </div>
        <div v-if="errorMsg" class="error-msg">
          {{ errorMsg }}
          <a v-if="errorMsg.includes('向量数据库')" href="javascript:void(0)" @click="goToVectorDbConfig">「向量库配置」</a>
          <template v-if="errorMsg.includes('向量数据库')">页面配置并激活</template>
        </div>
        <div class="modal-actions">
          <button class="btn btn-secondary" @click="showCreateModal = false">取消</button>
          <button class="btn btn-primary" @click="handleCreate">创建</button>
        </div>
      </div>
    </div>

    <!-- Upload Modal -->
    <div v-if="showUploadModal" class="modal-overlay" @click.self="showUploadModal = false">
      <div class="modal-content">
        <h3>上传文档</h3>
        <div class="form-group">
          <label>选择文件</label>
          <input type="file" @change="handleFileChange" accept=".txt,.md,.pdf,.docx,.xlsx,.json,.csv" />
          <p class="hint">支持 txt、md、pdf、docx、xlsx、json、csv</p>
        </div>
        <div v-if="uploadError" class="error-msg">{{ uploadError }}</div>
        <div class="modal-actions">
          <button class="btn btn-secondary" @click="showUploadModal = false">取消</button>
          <button class="btn btn-primary" :disabled="!uploadFile || uploading" @click="handleUpload">
            {{ uploading ? '上传中...' : '上传' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.knowledge-view {
  height: 100%;
  display: flex;
  flex-direction: column;
}

// --- Top bar ---
.page-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;

  > div {
    display: flex;
    align-items: baseline;
    gap: 10px;
  }

  h2 {
    font-size: 19px;
    font-weight: 600;
    letter-spacing: -0.015em;
    color: var(--text-primary);
  }
}

.count-tag {
  font-family: 'Sora', sans-serif;
  font-size: 11px;
  font-weight: 500;
  color: var(--text-muted);
  background: var(--bg-overlay);
  padding: 2px 9px;
  border-radius: 6px;
}

// --- Layout shell ---
.kb-layout {
  flex: 1;
  display: flex;
  border: 1px solid var(--border-subtle);
  border-radius: 14px;
  overflow: hidden;
  background: var(--bg-surface);
  min-height: 0;
  box-shadow:
    0 1px 3px rgba(0, 0, 0, 0.06),
    0 0 0 0.5px rgba(0, 0, 0, 0.04);
  animation: kbLayoutIn 0.5s cubic-bezier(0.22, 0.61, 0.36, 1);
}

@keyframes kbLayoutIn {
  from { opacity: 0; translate: 0 8px; }
  to { opacity: 1; translate: 0 0; }
}

// ============ LEFT SIDEBAR ============
.kb-sidebar {
  width: 286px;
  flex-shrink: 0;
  border-right: 1px solid var(--border-subtle);
  background:
    linear-gradient(180deg, rgba(212, 160, 64, 0.015) 0%, transparent 30%),
    var(--bg-elevated);
  display: flex;
  flex-direction: column;
}

.kb-search {
  margin: 14px 12px 10px;
  padding: 9px 12px 9px 32px;
  border-radius: 9px;
  background: var(--bg-surface);
  border: 1px solid transparent;
  color: var(--text-primary);
  font-size: 13px;
  outline: none;
  transition: all 0.25s ease;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='14' height='14' viewBox='0 0 24 24' fill='none' stroke='%2394a3b8' stroke-width='1.8' stroke-linecap='round' stroke-linejoin='round'%3E%3Ccircle cx='11' cy='11' r='8'%3E%3C/circle%3E%3Cline x1='21' y1='21' x2='16.65' y2='16.65'%3E%3C/line%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: 10px center;

  &::placeholder { color: var(--text-muted); font-size: 12.5px; }

  &:focus {
    border-color: var(--border-active);
    box-shadow: 0 0 0 3px rgba(212, 160, 64, 0.06);
  }
}

.kb-list {
  flex: 1;
  overflow-y: auto;
  padding: 2px 8px 14px;
}

.kb-list-item {
  padding: 13px 14px;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.22s cubic-bezier(0.22, 0.61, 0.36, 1);
  margin-bottom: 2px;
  border: 1px solid transparent;
  position: relative;

  &:hover {
    background: var(--bg-hover);
    translate: 2px 0;
  }

  &.active {
    background: linear-gradient(135deg, rgba(212, 160, 64, 0.08), rgba(212, 160, 64, 0.03));
    border-color: rgba(212, 160, 64, 0.15);
    box-shadow:
      inset 3px 0 0 0 var(--accent),
      0 2px 6px rgba(0, 0, 0, 0.04);
  }
}

.kb-item-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.kb-item-name {
  font-size: 13.5px;
  font-weight: 550;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  margin-right: 8px;
  line-height: 1.3;
}

.kb-item-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11.5px;
  color: var(--text-muted);
}

// ============ RIGHT DETAIL PANEL ============
.kb-detail {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow-y: auto;
}

.detail-placeholder {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  gap: 16px;
  font-size: 13.5px;
  opacity: 0.55;
  letter-spacing: 0.02em;
}

// --- Toolbar ---
.detail-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 28px;
  border-bottom: 1px solid var(--border-subtle);
  background: var(--bg-elevated);
  position: sticky;
  top: 0;
  z-index: 10;
}

.detail-tabs {
  display: flex;
  gap: 2px;
  background: var(--bg-overlay);
  border-radius: 9px;
  padding: 3px;
}

.tab-btn {
  padding: 6px 18px;
  border-radius: 7px;
  font-family: 'Sora', sans-serif;
  font-size: 12.5px;
  font-weight: 500;
  color: var(--text-muted);
  background: none;
  border: none;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.22, 0.61, 0.36, 1);

  &:hover { color: var(--text-secondary); }

  &.active {
    background: var(--bg-surface);
    color: var(--text-primary);
    box-shadow: 0 1px 2px rgba(0, 0, 0, 0.08);
  }
}

.toolbar-right {
  display: flex;
  gap: 6px;
  align-items: center;
}

// ============ DETAIL VIEW ============
.detail-view {
  padding: 24px 28px 32px;
  animation: detailFadeIn 0.35s ease;
}

@keyframes detailFadeIn {
  from { opacity: 0; translate: 0 6px; }
  to { opacity: 1; translate: 0 0; }
}

// --- Info columns ---
.info-columns {
  display: flex;
  gap: 24px;
  margin-bottom: 24px;
}

.info-col {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

// --- Spec card (the core building block) ---
.spec-card {
  background: var(--bg-elevated);
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
  overflow: hidden;
  animation: cardSlideIn 0.4s cubic-bezier(0.22, 0.61, 0.36, 1) backwards;

  &:nth-child(1) { animation-delay: 0.05s; }
  &:nth-child(2) { animation-delay: 0.12s; }
  &:nth-child(3) { animation-delay: 0.19s; }
}

@keyframes cardSlideIn {
  from { opacity: 0; translate: 0 10px; }
  to { opacity: 1; translate: 0 0; }
}

.spec-card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 18px;
  font-family: 'Sora', sans-serif;
  font-size: 11.5px;
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.06em;
  border-bottom: 1px solid var(--border-subtle);
  background: linear-gradient(180deg, rgba(148, 163, 184, 0.02), transparent);

  svg {
    opacity: 0.5;
    flex-shrink: 0;
  }
}

// Vector card: distinctive accent treatment
.spec-card-vector {
  border-color: rgba(212, 160, 64, 0.12);
  background:
    linear-gradient(160deg, rgba(212, 160, 64, 0.04) 0%, var(--bg-elevated) 60%);

  .spec-card-header {
    color: var(--accent);
    background: linear-gradient(180deg, rgba(212, 160, 64, 0.06), transparent);
    border-bottom-color: rgba(212, 160, 64, 0.08);

    svg { opacity: 0.7; }
  }
}

// Spec row block: for multi-line values like prompt
.spec-row-block {
  align-items: flex-start;
}

.spec-value-pre {
  font-family: 'JetBrains Mono', 'Menlo', monospace;
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--text-secondary);
  white-space: pre-wrap;
  background: rgba(148, 163, 184, 0.02);
  padding: 10px 14px;
  border-radius: 8px;
  max-height: 180px;
  overflow-y: auto;
}

// Docs card: full width
.spec-card-docs {
  animation-delay: 0.2s;

  .spec-card-header {
    justify-content: space-between;
  }
}

.docs-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

// --- Spec rows (property list) ---
.spec-rows {
  padding: 6px 0;
}

.spec-row {
  display: flex;
  align-items: baseline;
  padding: 10px 18px;
  transition: background 0.15s ease;

  &:hover {
    background: rgba(148, 163, 184, 0.02);
  }

  &:not(:last-child) {
    border-bottom: 1px solid rgba(148, 163, 184, 0.04);
  }
}

.spec-label {
  flex-shrink: 0;
  width: 80px;
  font-family: 'Sora', sans-serif;
  font-size: 11px;
  font-weight: 500;
  color: var(--text-muted);
  letter-spacing: 0.03em;
}

.spec-value {
  flex: 1;
  min-width: 0;
  font-size: 13.5px;
  color: var(--text-primary);
  font-weight: 480;
  line-height: 1.5;
  word-break: break-word;
}

.spec-value-name {
  font-weight: 600;
  font-size: 14px;
}

.spec-value-num {
  font-family: 'JetBrains Mono', monospace;
  font-weight: 600;
  font-size: 18px;
  color: var(--accent);
  letter-spacing: -0.02em;
}

.spec-mono {
  font-family: 'JetBrains Mono', 'Menlo', monospace;
  font-size: 12px;
  word-break: break-all;
  opacity: 0.85;
}

// ============ DOCUMENT TABLE ============
.doc-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;

  th {
    text-align: left;
    padding: 10px 18px;
    font-family: 'Sora', sans-serif;
    font-size: 10.5px;
    font-weight: 600;
    color: var(--text-muted);
    text-transform: uppercase;
    letter-spacing: 0.06em;
    border-bottom: 1px solid var(--border-subtle);
    white-space: nowrap;
  }

  td {
    padding: 10px 18px;
    border-bottom: 1px solid rgba(148, 163, 184, 0.04);
    color: var(--text-secondary);
    font-size: 12.5px;
    transition: background 0.15s;
  }

  tbody tr:last-child td { border-bottom: none; }

  tbody tr:hover td {
    background: rgba(212, 160, 64, 0.02);
  }
}

.doc-name-col {
  color: var(--text-primary);
  font-weight: 480;
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: flex;
  align-items: center;
  gap: 8px;
}

.doc-ext {
  font-family: 'Sora', sans-serif;
  font-size: 9px;
  font-weight: 600;
  text-transform: uppercase;
  color: var(--text-muted);
  background: var(--bg-overlay);
  padding: 2px 6px;
  border-radius: 4px;
  flex-shrink: 0;
  letter-spacing: 0.03em;
}

.doc-time-col {
  font-family: 'JetBrains Mono', monospace;
  font-size: 11.5px;
  opacity: 0.7;
  white-space: nowrap;
}

.docs-empty {
  padding: 32px 18px;
  text-align: center;
  font-size: 13px;
  color: var(--text-muted);
  opacity: 0.6;
}

.doc-actions-col {
  display: flex;
  gap: 4px;
  white-space: nowrap;
}

.doc-action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  transition: all 0.15s ease;

  &:hover {
    background: var(--bg-hover);
    color: var(--text-primary);
  }

  &.doc-action-danger:hover {
    background: rgba(239, 68, 68, 0.1);
    color: var(--danger);
  }
}

// ============ EDIT MODE ============
.detail-edit {
  padding: 24px 28px 32px;
  animation: detailFadeIn 0.3s ease;
}

.edit-card {
  background: var(--bg-elevated);
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
  overflow: hidden;
  animation: cardSlideIn 0.4s cubic-bezier(0.22, 0.61, 0.36, 1) backwards;

  .spec-card-header {
    border-bottom: 1px solid var(--border-subtle);
  }
}

.edit-fields {
  padding: 6px 0;
}

.edit-row {
  display: flex;
  align-items: flex-start;
  padding: 12px 18px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.04);

  &:last-child { border-bottom: none; }
}

.edit-label {
  flex-shrink: 0;
  width: 80px;
  padding-top: 10px;
  font-family: 'Sora', sans-serif;
  font-size: 11px;
  font-weight: 500;
  color: var(--text-muted);
  letter-spacing: 0.03em;
}

.edit-control {
  flex: 1;
  min-width: 0;

  input, select, textarea {
    width: 100%;
    padding: 9px 12px;
    font-size: 13.5px;
    font-family: inherit;
    background: var(--bg-surface);
    border: 1px solid var(--border-default);
    border-radius: 8px;
    color: var(--text-primary);
    outline: none;
    transition: all 0.2s ease;

    &:hover { border-color: var(--border-active); }

    &:focus {
      border-color: var(--accent);
      box-shadow: 0 0 0 3px rgba(212, 160, 64, 0.08);
    }
  }

  textarea { resize: vertical; }
}

.edit-row-pair {
  display: flex;
  gap: 16px;
  padding: 12px 18px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.04);

  .edit-half {
    flex: 1;

    .edit-label {
      width: auto;
      padding-top: 0;
      margin-bottom: 6px;
    }

    input {
      width: 100%;
      padding: 9px 12px;
      font-size: 13.5px;
      font-family: inherit;
      background: var(--bg-surface);
      border: 1px solid var(--border-default);
      border-radius: 8px;
      color: var(--text-primary);
      outline: none;
      transition: all 0.2s ease;

      &:hover { border-color: var(--border-active); }

      &:focus {
        border-color: var(--accent);
        box-shadow: 0 0 0 3px rgba(212, 160, 64, 0.08);
      }
    }
  }
}

.optional-tag {
  font-size: 10px;
  color: var(--text-muted);
  font-weight: 400;
  margin-left: 4px;
  background: var(--bg-overlay);
  padding: 1px 6px;
  border-radius: 5px;
  font-family: 'Sora', sans-serif;
}

.hint {
  font-size: 11.5px;
  color: var(--text-muted);
  margin-top: 6px;
  opacity: 0.7;
}

.edit-actions {
  display: flex;
  gap: 10px;
  padding-top: 20px;

  .btn {
    padding: 9px 24px;
    font-size: 13px;
    border-radius: 9px;
    font-weight: 520;
    transition: all 0.2s ease;
  }

  .btn-secondary {
    background: var(--bg-overlay);
    color: var(--text-secondary);
    border: 1px solid var(--border-subtle);

    &:hover {
      background: var(--bg-hover);
      color: var(--text-primary);
    }
  }

  .btn-primary {
    background: linear-gradient(135deg, rgba(212, 160, 64, 0.15), rgba(212, 160, 64, 0.08));
    color: var(--accent);
    border: 1px solid rgba(212, 160, 64, 0.2);
    font-weight: 550;

    &:hover:not(:disabled) {
      background: linear-gradient(135deg, rgba(212, 160, 64, 0.22), rgba(212, 160, 64, 0.12));
      border-color: rgba(212, 160, 64, 0.35);
    }

    &:disabled {
      opacity: 0.35;
      cursor: not-allowed;
    }
  }
}

// ============ MODALS ============
.form-section {
  margin-top: 18px;
  padding-top: 18px;
  border-top: 1px solid var(--border-subtle);
}

.section-hint {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 16px;
  opacity: 0.7;
}
</style>
