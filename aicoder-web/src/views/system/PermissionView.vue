<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { systemApi } from '@/api/system'
import type { RoleDTO, MenuTreeNode } from '@/types'

interface RoleWithMenus {
  role: RoleDTO
  menuIds: number[]
}

const loading = ref(false)
const saving = ref(false)
const roles = ref<RoleWithMenus[]>([])
const menuTree = ref<MenuTreeNode[]>([])
const selectedRoleId = ref<number | null>(null)
const checkedKeys = reactive<Set<number>>(new Set())
const message = ref('')
const messageType = ref<'success' | 'error'>('success')

const selectedRole = computed(() =>
  roles.value.find(r => r.role.id === selectedRoleId.value) || null
)

const fetchRoles = async () => {
  try {
    const res = await systemApi.permissionRoles()
    roles.value = res.data
  } catch {
    // ignore
  }
}

const fetchMenuTree = async () => {
  try {
    const res = await systemApi.permissionMenuTree()
    menuTree.value = res.data
  } catch {
    // ignore
  }
}

const loadData = async () => {
  loading.value = true
  try {
    await Promise.all([fetchRoles(), fetchMenuTree()])
  } finally {
    loading.value = false
  }
}

onMounted(loadData)

const selectRole = (roleWithMenus: RoleWithMenus) => {
  selectedRoleId.value = roleWithMenus.role.id
  checkedKeys.clear()
  roleWithMenus.menuIds.forEach(id => checkedKeys.add(id))
}

const showMessage = (text: string, type: 'success' | 'error') => {
  message.value = text
  messageType.value = type
  setTimeout(() => { message.value = '' }, 3000)
}

// 收集节点及所有后代节点的 id
const collectAllIds = (nodes: MenuTreeNode[]): number[] => {
  const ids: number[] = []
  const walk = (list: MenuTreeNode[]) => {
    for (const node of list) {
      ids.push(node.id)
      if (node.children?.length) walk(node.children)
    }
  }
  walk(nodes)
  return ids
}

// 检查父节点：如果所有子节点都选中，则选中父节点
const checkParent = (node: MenuTreeNode): boolean => {
  if (!node.children?.length) return checkedKeys.has(node.id)
  const allChildrenChecked = node.children.every(child => checkParent(child))
  if (allChildrenChecked) {
    checkedKeys.add(node.id)
  } else {
    checkedKeys.delete(node.id)
  }
  return checkedKeys.has(node.id)
}

// 重新计算所有父节点的选中状态（从顶层开始递归）
const recalcParents = (nodes: MenuTreeNode[]) => {
  for (const node of nodes) {
    if (node.children?.length) {
      recalcParents(node.children)
      const allChecked = node.children.every(child => checkedKeys.has(child.id))
      if (allChecked) {
        checkedKeys.add(node.id)
      } else {
        checkedKeys.delete(node.id)
      }
    }
  }
}

const onCheck = (node: MenuTreeNode, checked: boolean) => {
  if (checked) {
    // 选中节点 → 同时选中所有后代
    checkedKeys.add(node.id)
    const childIds = collectAllIds(node.children || [])
    childIds.forEach(id => checkedKeys.add(id))
  } else {
    // 取消选中 → 同时取消所有后代
    checkedKeys.delete(node.id)
    const childIds = collectAllIds(node.children || [])
    childIds.forEach(id => checkedKeys.delete(id))
  }
  // 重新计算整棵树的父节点状态
  recalcParents(menuTree.value)
}

// 半选状态：部分子节点选中
const isIndeterminate = (node: MenuTreeNode): boolean => {
  if (!node.children?.length) return false
  const childIds = collectAllIds(node.children)
  const checkedCount = childIds.filter(id => checkedKeys.has(id)).length
  return checkedCount > 0 && checkedCount < childIds.length
}

const isChecked = (node: MenuTreeNode): boolean => checkedKeys.has(node.id)

const handleSave = async () => {
  if (!selectedRoleId.value) return
  saving.value = true
  try {
    await systemApi.permissionAssign(selectedRoleId.value, Array.from(checkedKeys))
    // 更新本地 roles 数据
    const roleWithMenus = roles.value.find(r => r.role.id === selectedRoleId.value)
    if (roleWithMenus) {
      roleWithMenus.menuIds = Array.from(checkedKeys)
    }
    showMessage('保存成功', 'success')
  } catch {
    showMessage('保存失败，请重试', 'error')
  } finally {
    saving.value = false
  }
}

const expandKeys = reactive<Set<number>>(new Set())

const toggleExpand = (id: number) => {
  if (expandKeys.has(id)) {
    expandKeys.delete(id)
  } else {
    expandKeys.add(id)
  }
}

const isExpanded = (id: number): boolean => expandKeys.has(id)

// 默认展开所有节点
const expandAll = (nodes: MenuTreeNode[]) => {
  for (const node of nodes) {
    if (node.children?.length) {
      expandKeys.add(node.id)
      expandAll(node.children)
    }
  }
}

onMounted(() => {
  fetchMenuTree().then(() => expandAll(menuTree.value))
})
</script>

<template>
  <div class="permission-view">
    <div class="page-header">
      <h2>权限管理</h2>
    </div>

    <div v-if="loading" class="loading">加载中...</div>

    <div v-else class="permission-layout">
      <!-- 左侧：角色列表 -->
      <div class="left-panel card">
        <h3 class="panel-title">角色列表</h3>
        <div class="role-list">
          <div
            v-for="item in roles"
            :key="item.role.id"
            class="role-item"
            :class="{ active: selectedRoleId === item.role.id }"
            @click="selectRole(item)"
          >
            <div class="role-info">
              <span class="role-name">{{ item.role.name }}</span>
              <span class="role-code">{{ item.role.code }}</span>
            </div>
            <span class="tag" :class="item.role.status === 1 ? 'tag-green' : 'tag-gray'">
              {{ item.role.status === 1 ? '启用' : '禁用' }}
            </span>
          </div>
          <div v-if="roles.length === 0" class="empty-hint">暂无角色</div>
        </div>
      </div>

      <!-- 右侧：菜单树 -->
      <div class="right-panel card">
        <div class="panel-header">
          <h3 class="panel-title">
            {{ selectedRole ? `菜单权限 — ${selectedRole.role.name}` : '请选择角色' }}
          </h3>
        </div>

        <div v-if="!selectedRole" class="empty-hint center">请在左侧选择一个角色来分配菜单权限</div>

        <div v-else class="menu-tree-wrapper">
          <div v-if="menuTree.length === 0" class="empty-hint">暂无菜单数据</div>
          <div v-else class="tree-list">
            <div v-for="node in menuTree" :key="node.id" class="tree-node-root">
              <!-- 递归渲染树节点 -->
              <div class="tree-node-wrapper">
                <div class="tree-node" :style="{ paddingLeft: '12px' }">
                  <!-- 展开/折叠 -->
                  <span
                    v-if="node.children?.length"
                    class="expand-icon"
                    :class="{ expanded: isExpanded(node.id) }"
                    @click.stop="toggleExpand(node.id)"
                  >▶</span>
                  <span v-else class="expand-placeholder"></span>

                  <!-- 复选框 -->
                  <label class="checkbox-wrap" @click.stop>
                    <input
                      type="checkbox"
                      :checked="isChecked(node)"
                      :indeterminate="isIndeterminate(node)"
                      @change="onCheck(node, ($event.target as HTMLInputElement).checked)"
                    />
                    <span class="checkmark"></span>
                  </label>

                  <!-- 节点名称 -->
                  <span class="node-name">{{ node.name }}</span>
                </div>

                <!-- 子节点 -->
                <div v-if="node.children?.length && isExpanded(node.id)" class="tree-children">
                  <template v-for="child in node.children" :key="child.id">
                    <div class="tree-node-wrapper">
                      <div class="tree-node" :style="{ paddingLeft: '32px' }">
                        <span
                          v-if="child.children?.length"
                          class="expand-icon"
                          :class="{ expanded: isExpanded(child.id) }"
                          @click.stop="toggleExpand(child.id)"
                        >▶</span>
                        <span v-else class="expand-placeholder"></span>

                        <label class="checkbox-wrap" @click.stop>
                          <input
                            type="checkbox"
                            :checked="isChecked(child)"
                            :indeterminate="isIndeterminate(child)"
                            @change="onCheck(child, ($event.target as HTMLInputElement).checked)"
                          />
                          <span class="checkmark"></span>
                        </label>

                        <span class="node-name">{{ child.name }}</span>
                      </div>

                      <div v-if="child.children?.length && isExpanded(child.id)" class="tree-children">
                        <div v-for="grandchild in child.children" :key="grandchild.id" class="tree-node-wrapper">
                          <div class="tree-node" :style="{ paddingLeft: '52px' }">
                            <span class="expand-placeholder"></span>
                            <label class="checkbox-wrap" @click.stop>
                              <input
                                type="checkbox"
                                :checked="isChecked(grandchild)"
                                @change="onCheck(grandchild, ($event.target as HTMLInputElement).checked)"
                              />
                              <span class="checkmark"></span>
                            </label>
                            <span class="node-name">{{ grandchild.name }}</span>
                          </div>
                        </div>
                      </div>
                    </div>
                  </template>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 保存按钮 -->
        <div v-if="selectedRole" class="save-bar">
          <div v-if="message" class="save-msg" :class="messageType">{{ message }}</div>
          <button
            class="btn btn-primary"
            :disabled="saving"
            @click="handleSave"
          >
            {{ saving ? '保存中...' : '保存权限' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.permission-view {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.page-header {
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

.permission-layout {
  display: flex;
  gap: 20px;
  flex: 1;
  min-height: 0;
}

// ---- 左侧面板 ----
.left-panel {
  width: 250px;
  min-width: 250px;
  display: flex;
  flex-direction: column;
  padding: 16px;
}

.panel-title {
  font-family: 'Sora', sans-serif;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 12px;
  letter-spacing: 0.01em;
}

.role-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.role-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all 0.15s ease;
  border: 1px solid transparent;

  &:hover {
    background: var(--bg-hover);
  }

  &.active {
    background: var(--accent-glow);
    border-color: var(--accent);
  }
}

.role-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.role-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.role-code {
  font-size: 11px;
  color: var(--text-muted);
  font-family: 'JetBrains Mono', monospace;
}

// ---- 右侧面板 ----
.right-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 16px;
  min-width: 0;
}

.panel-header {
  margin-bottom: 12px;
}

.menu-tree-wrapper {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}

.tree-list {
  display: flex;
  flex-direction: column;
}

.tree-node-wrapper {
  user-select: none;
}

.tree-node {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  border-radius: var(--radius-xs);
  transition: background 0.1s ease;

  &:hover {
    background: var(--bg-hover);
  }
}

.expand-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  font-size: 10px;
  color: var(--text-muted);
  cursor: pointer;
  transition: transform 0.15s ease;
  flex-shrink: 0;

  &.expanded {
    transform: rotate(90deg);
  }
}

.expand-placeholder {
  display: inline-block;
  width: 18px;
  height: 18px;
  flex-shrink: 0;
}

.tree-children {
  // 子节点容器
}

// ---- 自定义复选框 ----
.checkbox-wrap {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  flex-shrink: 0;
  position: relative;
  width: 18px;
  height: 18px;

  input[type="checkbox"] {
    position: absolute;
    opacity: 0;
    width: 0;
    height: 0;

    &:checked + .checkmark {
      background: var(--accent);
      border-color: var(--accent);

      &::after {
        display: block;
      }
    }

    // 半选状态
    &:indeterminate + .checkmark {
      background: var(--accent);
      border-color: var(--accent);

      &::after {
        display: block;
        left: 3px;
        top: 50%;
        width: 8px;
        height: 2px;
        border: none;
        transform: translateY(-50%);
        background: var(--text-inverse);
        border-radius: 1px;
      }
    }
  }

  .checkmark {
    width: 16px;
    height: 16px;
    border: 1.5px solid var(--border-strong);
    border-radius: 3px;
    background: var(--input-bg);
    display: flex;
    align-items: center;
    justify-content: center;
    transition: all 0.15s ease;

    &::after {
      content: '';
      display: none;
      width: 4px;
      height: 8px;
      border: solid var(--text-inverse);
      border-width: 0 2px 2px 0;
      transform: rotate(45deg);
      position: absolute;
      top: 2px;
      left: 5px;
    }
  }

  &:hover .checkmark {
    border-color: var(--accent);
  }
}

.node-name {
  font-size: 14px;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

// ---- 保存栏 ----
.save-bar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 16px;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--border-default);
}

.save-msg {
  font-size: 13px;
  padding: 6px 12px;
  border-radius: var(--radius-sm);

  &.success {
    background: var(--surface-success);
    color: var(--success);
  }

  &.error {
    background: var(--surface-danger);
    color: var(--danger);
  }
}

// ---- 空状态 ----
.empty-hint {
  color: var(--text-muted);
  font-size: 14px;
  padding: 24px 0;
  text-align: center;

  &.center {
    display: flex;
    align-items: center;
    justify-content: center;
    flex: 1;
  }
}
</style>
