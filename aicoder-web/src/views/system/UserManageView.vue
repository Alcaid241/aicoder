<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { systemApi } from '@/api/system'
import type { UserDTO, RoleDTO } from '@/types'

// ========== 列表 & 分页 ==========
const users = ref<UserDTO[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const loading = ref(false)

const totalPages = computed(() => Math.ceil(total.value / size.value) || 1)
const pageNumbers = computed(() => {
  const pages: number[] = []
  const tp = totalPages.value
  const p = page.value
  let start = Math.max(1, p - 2)
  const end = Math.min(tp, start + 4)
  start = Math.max(1, end - 4)
  for (let i = start; i <= end; i++) pages.push(i)
  return pages
})

const fetchUsers = async () => {
  loading.value = true
  try {
    const res = await systemApi.userList(page.value, size.value, keyword.value || undefined)
    users.value = res.data.list
    total.value = res.data.total
  } catch {
    // ignore
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  page.value = 1
  fetchUsers()
}

const goToPage = (p: number) => {
  if (p < 1 || p > totalPages.value) return
  page.value = p
  fetchUsers()
}

onMounted(fetchUsers)

// ========== 新增 / 编辑弹窗 ==========
const showEditModal = ref(false)
const editingId = ref<number | null>(null)

const editForm = ref({
  username: '',
  password: '',
  nickname: '',
  email: ''
})
const editError = ref('')

const openCreateModal = () => {
  editingId.value = null
  editForm.value = { username: '', password: '', nickname: '', email: '' }
  editError.value = ''
  showEditModal.value = true
}

const openEditModal = (user: UserDTO) => {
  editingId.value = user.id
  editForm.value = {
    username: user.username,
    password: '',
    nickname: user.nickname || '',
    email: user.email || ''
  }
  editError.value = ''
  showEditModal.value = true
}

const handleSaveUser = async () => {
  editError.value = ''
  if (!editForm.value.username.trim()) {
    editError.value = '用户名不能为空'
    return
  }
  if (!editingId.value && !editForm.value.password.trim()) {
    editError.value = '密码不能为空'
    return
  }
  try {
    if (editingId.value) {
      const data: Record<string, string> = {}
      if (editForm.value.nickname.trim()) data.nickname = editForm.value.nickname.trim()
      if (editForm.value.email.trim()) data.email = editForm.value.email.trim()
      await systemApi.userUpdate(editingId.value, data)
    } else {
      await systemApi.userCreate({
        username: editForm.value.username.trim(),
        password: editForm.value.password.trim(),
        nickname: editForm.value.nickname.trim(),
        email: editForm.value.email.trim() || undefined
      })
    }
    showEditModal.value = false
    await fetchUsers()
  } catch {
    editError.value = '操作失败，请重试'
  }
}

// ========== 分配角色弹窗 ==========
const showRoleModal = ref(false)
const roleUserId = ref<number |0>(0)
const allRoles = ref<RoleDTO[]>([])
const selectedRoleIds = ref<number[]>([])
const roleLoading = ref(false)

const openRoleModal = async (user: UserDTO) => {
  roleUserId.value = user.id
  selectedRoleIds.value = user.roles.map(r => r.id)
  roleLoading.value = true
  showRoleModal.value = true
  try {
    const res = await systemApi.roleList()
    allRoles.value = res.data
  } catch {
    // ignore
  } finally {
    roleLoading.value = false
  }
}

const toggleRole = (roleId: number) => {
  const idx = selectedRoleIds.value.indexOf(roleId)
  if (idx >= 0) {
    selectedRoleIds.value.splice(idx, 1)
  } else {
    selectedRoleIds.value.push(roleId)
  }
}

const handleSaveRoles = async () => {
  try {
    await systemApi.userAssignRoles(roleUserId.value, selectedRoleIds.value)
    showRoleModal.value = false
    await fetchUsers()
  } catch {
    // ignore
  }
}

// ========== 重置密码弹窗 ==========
const showResetModal = ref(false)
const resetUserId = ref<number>(0)
const newPassword = ref('')
const resetError = ref('')

const openResetModal = (user: UserDTO) => {
  resetUserId.value = user.id
  newPassword.value = ''
  resetError.value = ''
  showResetModal.value = true
}

const handleResetPassword = async () => {
  resetError.value = ''
  if (!newPassword.value.trim()) {
    resetError.value = '新密码不能为空'
    return
  }
  try {
    await systemApi.userResetPassword(resetUserId.value, newPassword.value.trim())
    showResetModal.value = false
  } catch {
    resetError.value = '重置失败，请重试'
  }
}

// ========== 状态切换 & 删除 ==========
const handleToggleStatus = async (user: UserDTO) => {
  try {
    await systemApi.userToggleStatus(user.id)
    await fetchUsers()
  } catch {
    // ignore
  }
}

const handleDelete = async (id: number) => {
  if (!confirm('确定删除此用户？删除后不可恢复。')) return
  try {
    await systemApi.userDelete(id)
    await fetchUsers()
  } catch {
    // ignore
  }
}

// ========== 格式化 ==========
const formatDate = (dateStr: string) => {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
</script>

<template>
  <div class="user-manage-view">
    <!-- 页头 -->
    <div class="page-header">
      <h2>用户管理</h2>
      <div class="header-actions">
        <div class="search-box">
          <input
            v-model="keyword"
            placeholder="搜索用户名 / 昵称..."
            @keyup.enter="handleSearch"
          />
          <button class="btn btn-secondary btn-sm" @click="handleSearch">搜索</button>
        </div>
        <button class="btn btn-primary" @click="openCreateModal">+ 新增用户</button>
      </div>
    </div>

    <!-- 加载中 -->
    <div v-if="loading" class="loading">加载中...</div>

    <!-- 表格 -->
    <div v-else class="table-container card">
      <table>
        <thead>
          <tr>
            <th>用户名</th>
            <th>昵称</th>
            <th>邮箱</th>
            <th>状态</th>
            <th>角色</th>
            <th>创建时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="user in users" :key="user.id">
            <td class="td-username">{{ user.username }}</td>
            <td>{{ user.nickname || '-' }}</td>
            <td>{{ user.email || '-' }}</td>
            <td>
              <span
                class="tag clickable"
                :class="user.status === 1 ? 'tag-green' : 'tag-red'"
                @click="handleToggleStatus(user)"
                :title="'点击' + (user.status === 1 ? '禁用' : '启用')"
              >
                {{ user.status === 1 ? '启用' : '禁用' }}
              </span>
            </td>
            <td>
              <div class="role-tags">
                <span v-if="user.roles.length === 0" class="no-role">-</span>
                <span
                  v-for="role in user.roles"
                  :key="role.id"
                  class="tag tag-accent"
                >{{ role.name }}</span>
              </div>
            </td>
            <td class="td-time">{{ formatDate(user.createdAt) }}</td>
            <td>
              <div class="action-btns">
                <button class="btn btn-secondary btn-sm" @click="openEditModal(user)">编辑</button>
                <button class="btn btn-secondary btn-sm" @click="openRoleModal(user)">分配角色</button>
                <button class="btn btn-ghost btn-sm" @click="openResetModal(user)">重置密码</button>
                <button class="btn btn-danger btn-sm" @click="handleDelete(user.id)">删除</button>
              </div>
            </td>
          </tr>
          <tr v-if="users.length === 0">
            <td colspan="7" class="empty-row">暂无用户数据</td>
          </tr>
        </tbody>
      </table>

      <!-- 分页 -->
      <div v-if="total > 0" class="pagination">
        <button
          class="btn btn-ghost btn-sm"
          :disabled="page <= 1"
          @click="goToPage(page - 1)"
        >&laquo; 上一页</button>
        <div class="page-numbers">
          <button
            v-for="p in pageNumbers"
            :key="p"
            class="page-btn"
            :class="{ active: p === page }"
            @click="goToPage(p)"
          >{{ p }}</button>
        </div>
        <button
          class="btn btn-ghost btn-sm"
          :disabled="page >= totalPages"
          @click="goToPage(page + 1)"
        >下一页 &raquo;</button>
        <span class="page-info">共 {{ total }} 条</span>
      </div>
    </div>

    <!-- 新增 / 编辑弹窗 -->
    <div v-if="showEditModal" class="modal-overlay" @click.self="showEditModal = false">
      <div class="modal-content card">
        <h3>{{ editingId ? '编辑用户' : '新增用户' }}</h3>
        <div v-if="editError" class="error-msg">{{ editError }}</div>
        <div class="form-group">
          <label>用户名</label>
          <input
            v-model="editForm.username"
            placeholder="请输入用户名"
            :disabled="!!editingId"
          />
        </div>
        <div v-if="!editingId" class="form-group">
          <label>密码</label>
          <input
            v-model="editForm.password"
            type="password"
            placeholder="请输入密码"
          />
        </div>
        <div class="form-group">
          <label>昵称</label>
          <input v-model="editForm.nickname" placeholder="请输入昵称（可选）" />
        </div>
        <div class="form-group">
          <label>邮箱</label>
          <input v-model="editForm.email" type="email" placeholder="请输入邮箱（可选）" />
        </div>
        <div class="modal-actions">
          <button class="btn btn-secondary" @click="showEditModal = false">取消</button>
          <button class="btn btn-primary" @click="handleSaveUser">保存</button>
        </div>
      </div>
    </div>

    <!-- 分配角色弹窗 -->
    <div v-if="showRoleModal" class="modal-overlay" @click.self="showRoleModal = false">
      <div class="modal-content card">
        <h3>分配角色</h3>
        <div v-if="roleLoading" class="loading-sm">加载角色列表...</div>
        <div v-else class="role-list">
          <div v-if="allRoles.length === 0" class="empty-roles">暂无可分配的角色</div>
          <label
            v-for="role in allRoles"
            :key="role.id"
            class="role-item"
          >
            <input
              type="checkbox"
              :checked="selectedRoleIds.includes(role.id)"
              @change="toggleRole(role.id)"
            />
            <span class="role-name">{{ role.name }}</span>
            <span class="role-code">{{ role.code }}</span>
          </label>
        </div>
        <div class="modal-actions">
          <button class="btn btn-secondary" @click="showRoleModal = false">取消</button>
          <button class="btn btn-primary" @click="handleSaveRoles">保存</button>
        </div>
      </div>
    </div>

    <!-- 重置密码弹窗 -->
    <div v-if="showResetModal" class="modal-overlay" @click.self="showResetModal = false">
      <div class="modal-content card">
        <h3>重置密码</h3>
        <div v-if="resetError" class="error-msg">{{ resetError }}</div>
        <div class="form-group">
          <label>新密码</label>
          <input
            v-model="newPassword"
            type="password"
            placeholder="请输入新密码"
          />
        </div>
        <div class="modal-actions">
          <button class="btn btn-secondary" @click="showResetModal = false">取消</button>
          <button class="btn btn-primary" @click="handleResetPassword">确认重置</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.user-manage-view {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
  flex-shrink: 0;

  h2 {
    font-family: 'Sora', sans-serif;
    font-size: 18px;
    font-weight: 600;
    color: var(--text-primary);
  }
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.search-box {
  display: flex;
  align-items: center;
  gap: 6px;

  input {
    padding: 6px 12px;
    border-radius: var(--radius-sm);
    font-size: 13px;
    width: 220px;
    background: var(--input-bg);
    border: 1px solid var(--input-border);
    color: var(--text-primary);

    &::placeholder {
      color: var(--text-muted);
    }
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
  flex: 1;
  display: flex;
  flex-direction: column;

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
      white-space: nowrap;
    }

    td {
      color: var(--text-primary);
    }

    tbody tr {
      transition: background-color 0.15s ease;
      &:hover {
        background: var(--bg-hover);
      }
    }
  }
}

.td-username {
  font-weight: 500;
}

.td-time {
  font-size: 12px;
  color: var(--text-secondary);
  white-space: nowrap;
}

.empty-row {
  text-align: center !important;
  color: var(--text-muted) !important;
  padding: 40px !important;
}

// 状态标签可点击
.clickable {
  cursor: pointer;
  transition: opacity 0.2s ease;
  &:hover {
    opacity: 0.8;
  }
}

// 角色标签
.role-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.no-role {
  color: var(--text-muted);
}

// 操作按钮
.action-btns {
  display: flex;
  gap: 6px;
  flex-wrap: nowrap;
}

// 分页
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 16px;
  border-top: 1px solid var(--border-default);
  flex-shrink: 0;
}

.page-numbers {
  display: flex;
  gap: 4px;
}

.page-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-sm);
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
  background: transparent;
  transition: all 0.15s ease;
  border: 1px solid transparent;

  &:hover {
    background: var(--bg-hover);
    color: var(--text-primary);
  }

  &.active {
    background: var(--accent-glow);
    color: var(--accent);
    border-color: rgba(212, 160, 64, 0.25);
  }
}

.page-info {
  font-size: 12px;
  color: var(--text-muted);
  margin-left: 8px;
}

// 角色列表
.role-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 300px;
  overflow-y: auto;
  margin-bottom: 8px;
}

.role-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background-color 0.15s ease;
  border: 1px solid var(--border-subtle);

  &:hover {
    background: var(--bg-hover);
  }

  input[type="checkbox"] {
    width: 16px;
    height: 16px;
    accent-color: var(--accent);
    cursor: pointer;
  }

  .role-name {
    font-weight: 500;
    color: var(--text-primary);
  }

  .role-code {
    font-size: 12px;
    color: var(--text-muted);
    margin-left: auto;
    font-family: 'JetBrains Mono', monospace;
  }
}

.empty-roles {
  text-align: center;
  padding: 24px;
  color: var(--text-muted);
}

.loading-sm {
  text-align: center;
  padding: 24px;
  color: var(--text-muted);
}
</style>
