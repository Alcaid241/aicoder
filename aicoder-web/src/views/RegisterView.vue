<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/userStore'

const router = useRouter()
const userStore = useUserStore()

const form = ref({
  username: '',
  password: '',
  confirmPassword: '',
  nickname: '',
  email: ''
})
const loading = ref(false)
const errorMsg = ref('')

const handleRegister = async () => {
  if (!form.value.username || !form.value.password || !form.value.nickname || !form.value.email) {
    errorMsg.value = '请填写所有必填项'
    return
  }
  if (form.value.password !== form.value.confirmPassword) {
    errorMsg.value = '两次输入的密码不一致'
    return
  }
  if (form.value.password.length < 6) {
    errorMsg.value = '密码长度至少6位'
    return
  }
  loading.value = true
  errorMsg.value = ''
  try {
    await userStore.register({
      username: form.value.username,
      password: form.value.password,
      nickname: form.value.nickname,
      email: form.value.email
    })
    router.push('/login')
  } catch (e: any) {
    errorMsg.value = e.response?.data?.message || '注册失败，请重试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="register-page">
    <div class="register-card">
      <div class="card-header">
        <svg width="44" height="44" viewBox="0 0 44 44" fill="none">
          <rect width="44" height="44" rx="12" fill="var(--accent)" opacity="0.15"/>
          <polygon points="22,8 28,18 18,18" fill="var(--accent)"/>
          <polygon points="22,36 28,26 18,26" fill="var(--accent)" opacity="0.7"/>
        </svg>
        <h1>AI Coder</h1>
        <p>创建新账户</p>
      </div>
      <form @submit.prevent="handleRegister">
        <div class="form-group">
          <label>用户名</label>
          <input v-model="form.username" type="text" placeholder="请输入用户名" autocomplete="username" />
        </div>
        <div class="form-group">
          <label>密码</label>
          <input v-model="form.password" type="password" placeholder="请输入密码（至少6位）" autocomplete="new-password" />
        </div>
        <div class="form-group">
          <label>确认密码</label>
          <input v-model="form.confirmPassword" type="password" placeholder="请再次输入密码" autocomplete="new-password" />
        </div>
        <div class="form-group">
          <label>昵称</label>
          <input v-model="form.nickname" type="text" placeholder="请输入昵称" />
        </div>
        <div class="form-group">
          <label>邮箱</label>
          <input v-model="form.email" type="email" placeholder="请输入邮箱" autocomplete="email" />
        </div>
        <div v-if="errorMsg" class="error-msg">{{ errorMsg }}</div>
        <button type="submit" class="btn btn-primary register-btn" :disabled="loading">
          {{ loading ? '注册中...' : '注 册' }}
        </button>
      </form>
      <div class="footer-link">
        已有账号？<router-link to="/login">去登录</router-link>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.register-page {
  width: 100%;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-root);
  position: relative;

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    background:
      radial-gradient(ellipse 80% 60% at 30% 20%, rgba(212, 160, 64, 0.06), transparent),
      radial-gradient(ellipse 60% 70% at 70% 60%, rgba(212, 160, 64, 0.04), transparent);
    pointer-events: none;
  }
}

.register-card {
  width: 420px;
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  padding: 36px;
  position: relative;
  z-index: 1;
}

.card-header {
  text-align: center;
  margin-bottom: 28px;

  svg { margin: 0 auto 10px; display: block; }
  h1 {
    font-size: 24px;
    font-weight: 700;
    color: var(--text-primary);
    margin-bottom: 4px;
    font-family: 'Sora', sans-serif;
    letter-spacing: -0.02em;
  }
  p {
    color: var(--text-secondary);
    font-size: 14px;
  }
}

.error-msg {
  background: var(--surface-danger);
  border: 1px solid rgba(196, 86, 74, 0.25);
  color: var(--danger);
  padding: 10px 14px;
  border-radius: var(--radius-sm);
  font-size: 13px;
  margin-bottom: 16px;
}

.register-btn {
  width: 100%;
  height: 42px;
  font-size: 15px;
  font-weight: 600;
  margin-top: 4px;
}

.footer-link {
  text-align: center;
  margin-top: 20px;
  font-size: 14px;
  color: var(--text-secondary);
}
</style>
