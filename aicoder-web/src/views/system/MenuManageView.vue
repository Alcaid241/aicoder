<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { systemApi } from '@/api/system'
import type { MenuTreeNode, MenuDTO } from '@/types'

interface FlatRow {
  id: number
  parentId: number | null
  name: string
  path: string
  icon: string
  sort: number
  type: number
  level: number
  hasChildren: boolean
}

const tree = ref<MenuTreeNode[]>([])
const loading = ref(false)
const showModal = ref(false)
const editingId = ref<number | null>(null)
const expandedIds = ref<Set<number>>(new Set())

const form = ref({
  parentId: null as number | null,
  name: '',
  path: '',
  icon: '',
  sort: 0,
  type: 1
})

// 将树扁平化，根据展开状态过滤可见行
const flatRows = computed<FlatRow[]>(() => {
  const rows: FlatRow[] = []
  const walk = (nodes: MenuTreeNode[], level: number) => {
    for (const node of nodes) {
      rows.push({
        id: node.id,
        parentId: node.parentId,
        name: node.name,
        path: node.path,
        icon: node.icon,
        sort: node.sort,
        type: node.type,
        level,
        hasChildren: node.children && node.children.length > 0
      })
      // 只在展开时递归子节点
      if (node.children && node.children.length > 0 && expandedIds.value.has(node.id)) {
        walk(node.children, level + 1)
      }
    }
  }
  walk(tree.value, 0)
  return rows
})

// 所有菜单项用于父级下拉（扁平化，带层级前缀）
interface MenuOption {
  id: number
  label: string
}
const allMenuOptions = computed<MenuOption[]>(() => {
  const options: MenuOption[] = []
  const walk = (nodes: MenuTreeNode[], prefix: string) => {
    for (const node of nodes) {
      options.push({ id: node.id, label: prefix + node.name })
      if (node.children && node.children.length > 0) {
        walk(node.children, prefix + '--')
      }
    }
  }
  walk(tree.value, '')
  return options
})

const fetchTree = async () => {
  loading.value = true
  try {
    const res = await systemApi.menuTree()
    tree.value = res.data
    // 默认展开所有目录节点
    const collectDirIds = (nodes: MenuTreeNode[]) => {
      for (const node of nodes) {
        if (node.children && node.children.length > 0) {
          expandedIds.value.add(node.id)
          collectDirIds(node.children)
        }
      }
    }
    expandedIds.value = new Set()
    collectDirIds(tree.value)
  } catch {
    // ignore
  } finally {
    loading.value = false
  }
}

onMounted(fetchTree)

const toggleExpand = (id: number) => {
  if (expandedIds.value.has(id)) {
    expandedIds.value.delete(id)
  } else {
    expandedIds.value.add(id)
  }
  // 触发响应式更新
  expandedIds.value = new Set(expandedIds.value)
}

const typeLabel = (type: number) => {
  return type === 1 ? '目录' : '菜单'
}

const typeTagClass = (type: number) => {
  return type === 1 ? 'tag tag-blue' : 'tag tag-green'
}

const openCreateModal = () => {
  editingId.value = null
  form.value = {
    parentId: null,
    name: '',
    path: '',
    icon: '',
    sort: 0,
    type: 1
  }
  showModal.value = true
}

const openEditModal = (row: FlatRow) => {
  editingId.value = row.id
  form.value = {
    parentId: row.parentId,
    name: row.name,
    path: row.path,
    icon: row.icon,
    sort: row.sort,
    type: row.type
  }
  showModal.value = true
}

const openCreateChildModal = (parentId: number) => {
  editingId.value = null
  form.value = {
    parentId,
    name: '',
    path: '',
    icon: '',
    sort: 0,
    type: 2
  }
  showModal.value = true
}

const handleSave = async () => {
  if (!form.value.name.trim()) return
  try {
    if (editingId.value) {
      const data: Partial<MenuDTO> = {
        parentId: form.value.parentId,
        name: form.value.name,
        path: form.value.path,
        icon: form.value.icon,
        sort: form.value.sort,
        type: form.value.type
      }
      await systemApi.menuUpdate(editingId.value, data)
    } else {
      await systemApi.menuCreate({
        parentId: form.value.parentId,
        name: form.value.name,
        path: form.value.path,
        icon: form.value.icon,
        sort: form.value.sort,
        type: form.value.type
      })
    }
    showModal.value = false
    await fetchTree()
  } catch {
    // ignore
  }
}

const handleDelete = async (row: FlatRow) => {
  if (row.hasChildren) {
    alert('该菜单下存在子菜单，请先删除子菜单')
    return
  }
  if (!confirm(`确定删除菜单「${row.name}」吗？`)) return
  try {
    await systemApi.menuDelete(row.id)
    await fetchTree()
  } catch {
    // ignore
  }
}
</script>

<template>
  <div class="menu-manage-view">
    <div class="page-header">
      <h2>菜单管理</h2>
      <button class="btn btn-primary" @click="openCreateModal">+ 新增菜单</button>
    </div>

    <div v-if="loading" class="loading">加载中...</div>

    <div v-else class="table-container card">
      <table>
        <thead>
          <tr>
            <th>菜单名称</th>
            <th>图标</th>
            <th>路径</th>
            <th>类型</th>
            <th>排序</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in flatRows" :key="row.id">
            <td>
              <div class="name-cell" :style="{ paddingLeft: row.level * 24 + 'px' }">
                <span
                  v-if="row.hasChildren"
                  class="expand-arrow"
                  :class="{ expanded: expandedIds.has(row.id) }"
                  @click="toggleExpand(row.id)"
                >&#9654;</span>
                <span v-else class="expand-placeholder"></span>
                <span class="menu-name">{{ row.name }}</span>
              </div>
            </td>
            <td>
              <span v-if="row.icon" class="icon-cell">{{ row.icon }}</span>
              <span v-else class="text-muted">-</span>
            </td>
            <td>
              <span v-if="row.path" class="path-cell">{{ row.path }}</span>
              <span v-else class="text-muted">-</span>
            </td>
            <td>
              <span :class="typeTagClass(row.type)">{{ typeLabel(row.type) }}</span>
            </td>
            <td>{{ row.sort }}</td>
            <td>
              <div class="action-btns">
                <button class="btn btn-secondary btn-sm" @click="openEditModal(row)">编辑</button>
                <button class="btn btn-secondary btn-sm" @click="openCreateChildModal(row.id)">新增子菜单</button>
                <button class="btn btn-danger btn-sm" @click="handleDelete(row)">删除</button>
              </div>
            </td>
          </tr>
          <tr v-if="flatRows.length === 0">
            <td colspan="6" class="empty-row">暂无菜单数据</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 新增/编辑弹窗 -->
    <div v-if="showModal" class="modal-overlay" @click.self="showModal = false">
      <div class="modal-content card">
        <h3>{{ editingId ? '编辑菜单' : '新增菜单' }}</h3>
        <div class="form-group">
          <label>上级菜单</label>
          <select v-model.number="form.parentId">
            <option :value="null">无（顶级菜单）</option>
            <option
              v-for="opt in allMenuOptions"
              :key="opt.id"
              :value="opt.id"
            >{{ opt.label }}</option>
          </select>
        </div>
        <div class="form-group">
          <label>菜单名称</label>
          <input v-model="form.name" placeholder="请输入菜单名称" />
        </div>
        <div class="form-group">
          <label>路径</label>
          <input v-model="form.path" placeholder="请输入路径（如 /system/menu）" />
        </div>
        <div class="form-group">
          <label>图标</label>
          <input v-model="form.icon" placeholder="图标名称（可选）" />
        </div>
        <div class="form-row">
          <div class="form-group">
            <label>排序</label>
            <input v-model.number="form.sort" type="number" placeholder="排序值" />
          </div>
          <div class="form-group">
            <label>类型</label>
            <select v-model.number="form.type">
              <option :value="1">目录</option>
              <option :value="2">菜单</option>
            </select>
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
.menu-manage-view {
  height: 100%;
}

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

.loading {
  text-align: center;
  padding: 60px;
  color: var(--text-muted);
}

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

    td {
      color: var(--text-primary);
    }
  }
}

.empty-row {
  text-align: center !important;
  color: var(--text-muted) !important;
  padding: 40px !important;
}

.name-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.expand-arrow {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  font-size: 10px;
  color: var(--text-muted);
  cursor: pointer;
  transition: transform 0.2s;
  user-select: none;

  &.expanded {
    transform: rotate(90deg);
  }

  &:hover {
    color: var(--text-primary);
  }
}

.expand-placeholder {
  display: inline-block;
  width: 18px;
}

.menu-name {
  font-weight: 500;
}

.icon-cell {
  font-family: monospace;
  font-size: 13px;
  color: var(--text-secondary);
}

.path-cell {
  font-family: monospace;
  font-size: 13px;
  color: var(--text-secondary);
}

.text-muted {
  color: var(--text-muted);
}

.action-btns {
  display: flex;
  gap: 6px;
}

.tag {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 500;
}

.tag-blue {
  background: rgba(59, 130, 246, 0.15);
  color: #60a5fa;
}

.tag-green {
  background: rgba(34, 197, 94, 0.15);
  color: #4ade80;
}

.form-row {
  display: flex;
  gap: 16px;

  .form-group {
    flex: 1;
  }
}
</style>
