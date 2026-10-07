<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useUserStore } from '@/stores/userStore'
import request from '@/api/request'

const userStore = useUserStore()

const nickname = ref('')
const email = ref('')
const saving = ref(false)
const successMsg = ref('')

onMounted(async () => {
  if (!userStore.user) {
    await userStore.fetchUserInfo()
  }
  nickname.value = userStore.user?.nickname || ''
  email.value = userStore.user?.email || ''
})

const handleSave = async () => {
  saving.value = true
  successMsg.value = ''
  try {
    await request.put('/admin/user/info', {
      nickname: nickname.value,
      email: email.value
    })
    await userStore.fetchUserInfo()
    successMsg.value = '保存成功'
    setTimeout(() => { successMsg.value = '' }, 3000)
  } catch {
    // ignore
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="profile-view">
    <h2>个人信息</h2>
    <div class="profile-card card">
      <div class="info-row">
        <label>用户名</label>
        <span class="info-value">{{ userStore.user?.username || '-' }}</span>
      </div>
      <div class="form-group">
        <label>昵称</label>
        <input v-model="nickname" placeholder="请输入昵称" />
      </div>
      <div class="form-group">
        <label>邮箱</label>
        <input v-model="email" type="email" placeholder="请输入邮箱" />
      </div>
      <div v-if="successMsg" class="success-msg">{{ successMsg }}</div>
      <button class="btn btn-primary" :disabled="saving" @click="handleSave">
        {{ saving ? '保存中...' : '保存修改' }}
      </button>
    </div>
  </div>
</template>

<style scoped lang="scss">
.profile-view {
  max-width: 560px;
  height: 100%;

  h2 {
    font-family: 'Sora', sans-serif;
    font-size: 18px;
    font-weight: 600;
    color: var(--text-primary);
    margin-bottom: 20px;
  }
}

.profile-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.info-row {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 0;
  border-bottom: 1px solid var(--border-default);
  margin-bottom: 8px;

  label {
    font-weight: 500;
    color: var(--text-secondary);
    min-width: 80px;
  }
}

.info-value {
  color: var(--text-primary);
  font-weight: 500;
}

.success-msg {
  background: var(--surface-success);
  border: 1px solid rgba(74, 158, 110, 0.25);
  color: var(--success);
  padding: 8px 12px;
  border-radius: var(--radius-sm);
  font-size: 13px;
}
</style>
