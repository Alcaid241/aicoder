<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { systemApi } from '@/api/system'
import type { RoleDTO, MenuTreeNode } from '@/types'

const roles = ref<RoleDTO[]>([])
const loading = ref(false)

// ---- 角色编辑弹窗 ----
const showRoleModal = ref(false)
const editingRoleId = ref<number | null>(null)
const roleForm = ref({ name: '', code: '', sort: 0, remark: '' })

// ---- 分配菜单弹窗 ----
const showMenuModal = ref(false)
const menuLoading = ref(false)
const assigningRoleId = ref<number | null>(null)
const menuTree = ref<MenuTreeNode[]>([])
const checkedMenuIds = ref<Set<number>>(new Set())

// 扁平化菜单树，用于勾选列表渲染
interface FlatMenuNode {
  id: number
  name: string
  level: number
}

const flatMenuNodes = computed<FlatMenuNode[]>(() => {
  const nodes: FlatMenuNode[] = []
  const walk = (items: MenuTreeNode[], level: number) => {
    for (const item of items) {
      nodes.push({ id: item.id, name: item.name, level })
      if (item.children?.length) {
        walk(item.children, level + 1)
      }
    }
  }
  walk(menuTree.value, 0)
  return nodes
})

// ---- 加载角色列表 ----
const fetchRoles = async () => {
  loading.value = true
  try {
    const res = await systemApi.roleList()
    roles.value = res.data
  } catch {
    // ignore
  } finally {
    loading.value = false
  }
}

onMounted(fetchRoles)

// ---- 新增角色 ----
const openCreateModal = () => {
  editingRoleId.value = null
  roleForm.value = { name: '', code: '', sort: 0, remark: '' }
  showRoleModal.value = true
}

// ---- 编辑角色 ----
const openEditModal = (role: RoleDTO) => {
  editingRoleId.value = role.id
  roleForm.value = {
    name: role.name,
    code: role.code,
    sort: role.sort,
    remark: role.remark || ''
  }
  showRoleModal.value = true
}

// ---- 保存角色（新增 / 编辑） ----
const handleSaveRole = async () => {
  try {
    if (editingRoleId.value) {
      await systemApi.roleUpdate(editingRoleId.value, {
        name: roleForm.value.name,
        sort: roleForm.value.sort,
        remark: roleForm.value.remark
      })
    } else {
      await systemApi.roleCreate({
        name: roleForm.value.name,
        code: roleForm.value.code,
        sort: roleForm.value.sort,
        remark: roleForm.value.remark
      })
    }
    showRoleModal.value = false
    await fetchRoles()
  } catch {
    // ignore
  }
}

// ---- 删除角色 ----
const handleDelete = async (id: number) => {
  if (!confirm('确定删除此角色？')) return
  try {
    await systemApi.roleDelete(id)
    await fetchRoles()
  } catch {
    // ignore
  }
}

// ---- 分配菜单 ----
const openMenuModal = async (role: RoleDTO) => {
  assigningRoleId.value = role.id
  checkedMenuIds.value = new Set()
  showMenuModal.value = true
  menuLoading.value = true
  try {
    const [treeRes, idsRes] = await Promise.all([
      systemApi.menuTree(),
      systemApi.roleMenuIds(role.id)
    ])
    menuTree.value = treeRes.data
    checkedMenuIds.value = new Set(idsRes.data)
  } catch {
    // ignore
  } finally {
    menuLoading.value = false
  }
}

// ---- 菜单勾选 ----
const toggleMenuCheck = (id: number) => {
  const next = new Set(checkedMenuIds.value)
  if (next.has(id)) {
    next.delete(id)
  } else {
    next.add(id)
  }
  checkedMenuIds.value = next
}

// ---- 递归收集所有菜单 ID ----
const collectAllIds = (nodes: MenuTreeNode[]): number[] => {
  const ids: number[] = []
  for (const node of nodes) {
    ids.push(node.id)
    if (node.children?.length) {
      ids.push(...collectAllIds(node.children))
    }
  }
  return ids
}

// ---- 全选 / 取消全选 ----
const toggleAll = (checked: boolean) => {
  if (checked) {
    checkedMenuIds.value = new Set(collectAllIds(menuTree.value))
  } else {
    checkedMenuIds.value = new Set()
  }
}

const isAllChecked = () => {
  if (menuTree.value.length === 0) return false
  const allIds = collectAllIds(menuTree.value)
  return allIds.length > 0 && allIds.every(id => checkedMenuIds.value.has(id))
}

// ---- 保存菜单分配 ----
const handleSaveMenus = async () => {
  if (!assigningRoleId.value) return
  try {
    await systemApi.roleAssignMenus(assigningRoleId.value, Array.from(checkedMenuIds.value))
    showMenuModal.value = false
  } catch {
    // ignore
  }
}
</script>

<template>
  <div class="role-view">
    <div class="page-header">
      <h2>角色管理</h2>
      <button class="btn btn-primary" @click="openCreateModal">+ 新增角色</button>
    </div>

    <div v-if="loading" class="loading">加载中...</div>

    <div v-else class="table-container card">
      <table>
        <thead>
          <tr>
            <th>角色名</th>
            <th>编码</th>
            <th>排序</th>
            <th>备注</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="role in roles" :key="role.id">
            <td>{{ role.name }}</td>
            <td><span class="tag tag-blue">{{ role.code }}</span></td>
            <td>{{ role.sort }}</td>
            <td>{{ role.remark || '-' }}</td>
            <td>
              <div class="action-btns">
                <button class="btn btn-secondary btn-sm" @click="openEditModal(role)">编辑</button>
                <button class="btn btn-secondary btn-sm" @click="openMenuModal(role)">分配菜单</button>
                <button class="btn btn-danger btn-sm" @click="handleDelete(role.id)">删除</button>
              </div>
            </td>
          </tr>
          <tr v-if="roles.length === 0">
            <td colspan="5" class="empty-row">暂无角色</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 新增 / 编辑角色弹窗 -->
    <div v-if="showRoleModal" class="modal-overlay" @click.self="showRoleModal = false">
      <div class="modal-content card">
        <h3>{{ editingRoleId ? '编辑角色' : '新增角色' }}</h3>
        <div class="form-group">
          <label>角色名</label>
          <input v-model="roleForm.name" placeholder="请输入角色名" />
        </div>
        <div class="form-group">
          <label>编码</label>
          <input
            v-model="roleForm.code"
            placeholder="请输入角色编码"
            :disabled="!!editingRoleId"
          />
        </div>
        <div class="form-group">
          <label>排序</label>
          <input v-model.number="roleForm.sort" type="number" placeholder="排序值" />
        </div>
        <div class="form-group">
          <label>备注</label>
          <textarea v-model="roleForm.remark" rows="3" placeholder="备注（可选）" />
        </div>
        <div class="modal-actions">
          <button class="btn btn-secondary" @click="showRoleModal = false">取消</button>
          <button class="btn btn-primary" @click="handleSaveRole">保存</button>
        </div>
      </div>
    </div>

    <!-- 分配菜单弹窗 -->
    <div v-if="showMenuModal" class="modal-overlay" @click.self="showMenuModal = false">
      <div class="modal-content card menu-modal">
        <h3>分配菜单</h3>
        <div v-if="menuLoading" class="loading">加载中...</div>
        <template v-else>
          <div class="tree-toolbar">
            <label class="check-all">
              <input
                type="checkbox"
                :checked="isAllChecked()"
                @change="toggleAll(($event.target as HTMLInputElement).checked)"
              />
              <span>全选</span>
            </label>
          </div>
          <div class="menu-tree">
            <div
              v-for="node in flatMenuNodes"
              :key="node.id"
              class="tree-node"
              :style="{ paddingLeft: node.level * 24 + 8 + 'px' }"
            >
              <input
                type="checkbox"
                :checked="checkedMenuIds.has(node.id)"
                @change="toggleMenuCheck(node.id)"
              />
              <span class="node-name">{{ node.name }}</span>
            </div>
            <div v-if="flatMenuNodes.length === 0" class="empty-tree">暂无菜单数据</div>
          </div>
        </template>
        <div class="modal-actions">
          <button class="btn btn-secondary" @click="showMenuModal = false">取消</button>
          <button class="btn btn-primary" :disabled="menuLoading" @click="handleSaveMenus">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.role-view {
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

.action-btns {
  display: flex;
  gap: 6px;
}

.menu-modal {
  max-width: 520px;
  max-height: 80vh;
  display: flex;
  flex-direction: column;

  h3 {
    margin-bottom: 12px;
  }

  .loading {
    padding: 40px;
  }
}

.tree-toolbar {
  padding: 8px 0;
  border-bottom: 1px solid var(--border-default);
  margin-bottom: 8px;

  .check-all {
    display: flex;
    align-items: center;
    gap: 6px;
    cursor: pointer;
    font-size: 14px;
    color: var(--text-secondary);

    input[type='checkbox'] {
      cursor: pointer;
    }
  }
}

.menu-tree {
  flex: 1;
  overflow-y: auto;
  padding-bottom: 8px;
}

.tree-node {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-top: 6px;
  padding-bottom: 6px;
  padding-right: 8px;
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.15s;

  &:hover {
    background: var(--bg-overlay);
  }

  input[type='checkbox'] {
    cursor: pointer;
    flex-shrink: 0;
  }

  .node-name {
    font-size: 14px;
    color: var(--text-primary);
  }
}

.empty-tree {
  text-align: center;
  padding: 24px;
  color: var(--text-muted);
  font-size: 14px;
}
</style>
