<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { marked } from 'marked'
import { skillApi } from '@/api/skill'
import type { SkillDTO, CreateSkillRequest } from '@/types'

const allSkills = ref<SkillDTO[]>([])
const loading = ref(false)
const statusFilter = ref<string>('ALL')
const statuses = ['ALL', 'PENDING_APPROVAL', 'DRAFT', 'ACTIVE', 'REJECTED', 'ARCHIVED']

const skills = computed(() =>
  statusFilter.value === 'ALL'
    ? allSkills.value
    : allSkills.value.filter(s => s.status === statusFilter.value)
)

const fetchList = async () => {
  loading.value = true
  try {
    const res = await skillApi.list()
    allSkills.value = res.data
  } catch {
    // ignore
  } finally {
    loading.value = false
  }
}
onMounted(fetchList)

// 详情 modal
const detailSkill = ref<SkillDTO | null>(null)
const detailHtml = computed(() =>
  detailSkill.value ? marked.parse(detailSkill.value.content) as string : '')
const openDetail = (s: SkillDTO) => { detailSkill.value = s }

// 新建/编辑 modal
const showForm = ref(false)
const editingId = ref<number | null>(null)
const defaultForm: CreateSkillRequest = { name: '', displayName: '', description: '', content: '', source: 'MANUAL' }
const form = ref<CreateSkillRequest>({ ...defaultForm })
const openCreate = () => { editingId.value = null; form.value = { ...defaultForm }; showForm.value = true }
const openEdit = (s: SkillDTO) => {
  editingId.value = s.id
  form.value = { name: s.name, displayName: s.displayName || '', description: s.description, content: s.content, category: s.category || '', tags: s.tags || '', source: s.source }
  showForm.value = true
}
const handleSave = async () => {
  try {
    if (editingId.value) await skillApi.update(editingId.value, form.value)
    else await skillApi.create(form.value)
    showForm.value = false
    await fetchList()
  } catch { /* ignore */ }
}

// 生成 modal
const showGenerate = ref(false)
const intent = ref('')
const generating = ref(false)
const openGenerate = () => { intent.value = ''; showGenerate.value = true }
const handleGenerate = async () => {
  if (!intent.value.trim()) return
  generating.value = true
  try {
    await skillApi.generate({ intent: intent.value })
    showGenerate.value = false
    await fetchList()
  } catch { /* ignore */ } finally {
    generating.value = false
  }
}

// 审批/归档动作
const handleAction = async (s: SkillDTO, action: 'submit' | 'approve' | 'reject' | 'archive') => {
  const labels: Record<string, string> = { submit: '提审', approve: '通过', reject: '拒绝', archive: '归档' }
  if (!confirm(`确定${labels[action]}技能「${s.name}」？`)) return
  try {
    await skillApi[action](s.id)
    await fetchList()
  } catch { /* ignore */ }
}

const statusTagClass = (s: string) => ({
  ACTIVE: 'tag-green', PENDING_APPROVAL: 'tag-orange', DRAFT: 'tag-gray',
  REJECTED: 'tag-red', ARCHIVED: 'tag-gray'
}[s] || 'tag-gray')
const statusLabel = (s: string) => ({
  ACTIVE: '已生效', PENDING_APPROVAL: '待审批', DRAFT: '草稿', REJECTED: '已拒绝', ARCHIVED: '已归档'
}[s] || s)
</script>

<template>
  <div class="skill-mgmt-view">
    <div class="page-header">
      <h2>技能管理</h2>
      <div class="header-actions">
        <button class="btn btn-secondary" @click="openGenerate">✨ 生成技能</button>
        <button class="btn btn-primary" @click="openCreate">+ 新建技能</button>
      </div>
    </div>

    <div class="filter-tabs">
      <button v-for="s in statuses" :key="s" class="filter-tab" :class="{ active: statusFilter === s }" @click="statusFilter = s">
        {{ s === 'ALL' ? '全部' : statusLabel(s) }}
      </button>
    </div>

    <div v-if="loading" class="loading">加载中...</div>
    <div v-else class="table-container card">
      <table>
        <thead>
          <tr>
            <th>技能名</th><th>描述</th><th>状态</th><th>来源</th><th>质量分</th><th>版本</th><th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="s in skills" :key="s.id">
            <td><span class="tag tag-blue">{{ s.name }}</span></td>
            <td class="desc-cell">{{ s.description }}</td>
            <td><span class="tag" :class="statusTagClass(s.status)">{{ statusLabel(s.status) }}</span></td>
            <td><span class="tag" :class="s.source === 'AUTO_GENERATED' ? 'tag-purple' : 'tag-gray'">{{ s.source === 'AUTO_GENERATED' ? '自动' : '手动' }}</span></td>
            <td>{{ s.qualityScore ?? '-' }}</td>
            <td>v{{ s.version }}</td>
            <td>
              <div class="action-btns">
                <button class="btn btn-secondary btn-sm" @click="openDetail(s)">查看</button>
                <button class="btn btn-secondary btn-sm" @click="openEdit(s)">编辑</button>
                <button v-if="s.status === 'DRAFT'" class="btn btn-primary btn-sm" @click="handleAction(s, 'submit')">提审</button>
                <button v-if="s.status === 'PENDING_APPROVAL'" class="btn btn-primary btn-sm" @click="handleAction(s, 'approve')">通过</button>
                <button v-if="s.status === 'PENDING_APPROVAL'" class="btn btn-danger btn-sm" @click="handleAction(s, 'reject')">拒绝</button>
                <button v-if="s.status === 'ACTIVE'" class="btn btn-danger btn-sm" @click="handleAction(s, 'archive')">归档</button>
              </div>
            </td>
          </tr>
          <tr v-if="skills.length === 0"><td colspan="7" class="empty-row">暂无技能</td></tr>
        </tbody>
      </table>
    </div>

    <!-- 详情 modal（marked 渲染） -->
    <div v-if="detailSkill" class="modal-overlay" @click.self="detailSkill = null">
      <div class="modal-content card detail-modal">
        <h3>{{ detailSkill.name }} <span class="tag" :class="statusTagClass(detailSkill.status)">{{ statusLabel(detailSkill.status) }}</span></h3>
        <div class="detail-meta">
          <span>来源：{{ detailSkill.source === 'AUTO_GENERATED' ? '自动生成' : '手动' }}</span>
          <span>质量分：{{ detailSkill.qualityScore ?? '-' }}</span>
          <span>版本：v{{ detailSkill.version }}</span>
        </div>
        <div class="md-preview" v-html="detailHtml"></div>
        <div class="modal-actions">
          <button class="btn btn-secondary" @click="detailSkill = null">关闭</button>
        </div>
      </div>
    </div>

    <!-- 新建/编辑 modal -->
    <div v-if="showForm" class="modal-overlay" @click.self="showForm = false">
      <div class="modal-content card">
        <h3>{{ editingId ? '编辑技能' : '新建技能' }}</h3>
        <div class="form-group"><label>技能名 (kebab-case) <span class="required">*</span></label><input v-model="form.name" placeholder="如 code-review-checklist" /></div>
        <div class="form-group"><label>展示名</label><input v-model="form.displayName" placeholder="可选" /></div>
        <div class="form-group"><label>描述 <span class="required">*</span></label><input v-model="form.description" placeholder="一句话：何时使用" /></div>
        <div class="form-group"><label>SKILL.md 内容 <span class="required">*</span></label><textarea v-model="form.content" rows="10" placeholder="---&#10;name: xxx&#10;description: xxx&#10;---&#10;# 技能正文"></textarea></div>
        <div class="modal-actions">
          <button class="btn btn-secondary" @click="showForm = false">取消</button>
          <button class="btn btn-primary" @click="handleSave">保存</button>
        </div>
      </div>
    </div>

    <!-- 生成 modal -->
    <div v-if="showGenerate" class="modal-overlay" @click.self="!generating && (showGenerate = false)">
      <div class="modal-content card">
        <h3>✨ 生成技能</h3>
        <div class="form-group"><label>描述你想要的技能意图 <span class="required">*</span></label><textarea v-model="intent" rows="5" placeholder="如：每次帮我给代码改动写约定式 commit message"></textarea></div>
        <div class="hint">将调用 LLM 按 generate-skill 元技能模板起草，自动打分后进入审批队列。</div>
        <div class="modal-actions">
          <button class="btn btn-secondary" :disabled="generating" @click="showGenerate = false">取消</button>
          <button class="btn btn-primary" :disabled="generating || !intent.trim()" @click="handleGenerate">{{ generating ? '生成中...' : '生成' }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.skill-mgmt-view { height: 100%; }
.page-header {
  display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px;
  h2 { font-family: 'Sora', sans-serif; font-size: 18px; font-weight: 600; color: var(--text-primary); }
  .header-actions { display: flex; gap: 8px; }
}
.filter-tabs { display: flex; gap: 6px; margin-bottom: 16px; flex-wrap: wrap; }
.filter-tab {
  padding: 6px 14px; border-radius: 999px; border: 1px solid var(--border-default);
  background: transparent; color: var(--text-secondary); font-size: 13px; cursor: pointer;
  &.active { background: var(--accent); color: #fff; border-color: var(--accent); }
}
.loading { text-align: center; padding: 60px; color: var(--text-muted); }
.table-container { overflow-x: auto; padding: 0;
  table { width: 100%; border-collapse: collapse;
    th, td { padding: 12px 14px; text-align: left; border-bottom: 1px solid var(--border-default); font-size: 14px; }
    th { background: var(--bg-overlay); font-weight: 600; color: var(--text-secondary); font-family: 'Sora', sans-serif; font-size: 12px; }
    td { color: var(--text-primary); }
  }
}
.desc-cell { max-width: 320px; color: var(--text-secondary); }
.empty-row { text-align: center !important; color: var(--text-muted) !important; padding: 40px !important; }
.action-btns { display: flex; gap: 6px; flex-wrap: wrap; }
.detail-modal {
  max-width: 820px; width: 92%;
  display: flex; flex-direction: column;
  max-height: 88vh;           /* 覆盖全局 modal-content 的 80vh，给查看更多空间 */
  h3 { flex: 0 0 auto; }
  .detail-meta { flex: 0 0 auto; }
  .modal-actions { flex: 0 0 auto; }
}
.detail-meta { display: flex; gap: 16px; color: var(--text-muted); font-size: 12px; margin-bottom: 12px; }
.md-preview {
  flex: 1 1 auto;             /* 弹性填充 header/meta 之外的剩余空间 */
  min-height: 0;              /* flex 子项要能收缩+滚动，必须 min-height:0 */
  overflow: auto;             /* 内容超出时内部滚动，header/关闭按钮保持可见 */
  padding: 12px; border: 1px solid var(--border-default); border-radius: 6px;
  background: var(--bg-overlay); color: var(--text-primary); line-height: 1.6;
  :deep(h1), :deep(h2), :deep(h3) { font-family: 'Sora', sans-serif; margin: 0.6em 0 0.3em; }
  :deep(code) { background: rgba(127,127,127,0.2); padding: 2px 5px; border-radius: 3px; font-size: 0.9em; }
  :deep(pre) { background: rgba(0,0,0,0.3); padding: 10px; border-radius: 6px; overflow-x: auto; }
  :deep(table) { border-collapse: collapse; margin: 8px 0; th, td { border: 1px solid var(--border-default); padding: 6px 10px; } }
}
.hint { color: var(--text-muted); font-size: 12px; margin-top: -6px; }
.required { color: #e74c3c; }
</style>
