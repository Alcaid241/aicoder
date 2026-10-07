import { createRouter, createWebHashHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/RegisterView.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    meta: { requiresAuth: true },
    redirect: '/home',
    children: [
      { path: 'home', name: 'Home', component: () => import('@/views/HomeView.vue') },
      { path: 'chat', name: 'Chat', component: () => import('@/views/chat/ChatView.vue') },
      { path: 'knowledge', name: 'Knowledge', component: () => import('@/views/knowledge/KnowledgeView.vue') },
      { path: 'rag', name: 'Rag', component: () => import('@/views/rag/RagChatView.vue') },
      { path: 'sql', name: 'Sql', component: () => import('@/views/sql/SqlView.vue') },
      { path: 'workflow', name: 'WorkflowList', component: () => import('@/views/workflow/WorkflowListView.vue') },
      { path: 'workflow/:id', name: 'WorkflowEditor', component: () => import('@/views/workflow/WorkflowEditorView.vue') },
      { path: 'model/provider', name: 'ModelProvider', component: () => import('@/views/model/ProviderConfigView.vue') },
      { path: 'model/config', name: 'ModelConfig', component: () => import('@/views/model/ModelConfigView.vue') },
      { path: 'model/vectordb', name: 'VectorDb', component: () => import('@/views/model/VectorDbConfigView.vue') },
      { path: 'skill', name: 'Skill', component: () => import('@/views/skill/SkillManageView.vue') },
      { path: 'profile', name: 'Profile', component: () => import('@/views/admin/ProfileView.vue') },
      { path: 'system/user', name: 'SystemUser', component: () => import('@/views/system/UserManageView.vue') },
      { path: 'system/role', name: 'SystemRole', component: () => import('@/views/system/RoleManageView.vue') },
      { path: 'system/permission', name: 'SystemPermission', component: () => import('@/views/system/PermissionView.vue') },
      { path: 'system/menu', name: 'SystemMenu', component: () => import('@/views/system/MenuManageView.vue') }
    ]
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('token')
  if (to.meta.requiresAuth !== false && !token) {
    next('/login')
  } else if ((to.path === '/login' || to.path === '/register') && token) {
    next('/')
  } else {
    next()
  }
})

export default router
