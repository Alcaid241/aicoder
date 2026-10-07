export interface User {
  id: number
  username: string
  nickname: string
  email: string
  avatar: string
}

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  token: string
  username: string
  nickname: string
}

export interface RegisterRequest {
  username: string
  password: string
  nickname: string
  email: string
}

export interface ModelInfo {
  modelId: string
  modelName: string
  provider: string
  description: string
}

export interface Conversation {
  id: number
  title: string
  modelId: string
  type: string
  createdAt: string
}

export interface ChatMessage {
  id: number
  role: 'user' | 'assistant'
  content: string
  modelId: string
  createdAt: string
}

export interface ChatRequest {
  model: string
  message: string
  conversationId?: number | null
}

export interface KnowledgeBase {
  id: number
  userId: number
  name: string
  type: string
  systemPrompt: string
  databaseName: string
  jdbcUrl: string
  dbUsername: string
  dbPassword: string
  vectorDbType: string
  vectorCollection: string
  description: string
  documentCount: number
  status: number
  createdAt: string
  updatedAt: string
}

export interface KnowledgeBaseCreateRequest {
  name: string
  type: string
  systemPrompt?: string
  databaseName?: string
  jdbcUrl?: string
  dbUsername?: string
  dbPassword?: string
  description?: string
  vectorDbConfigId?: number
}

export interface KnowledgeBaseUpdateRequest {
  name: string
  type: string
  systemPrompt?: string
  databaseName?: string
  jdbcUrl?: string
  dbUsername?: string
  dbPassword?: string
  description?: string
  vectorDbConfigId?: number
}

export interface RagChatRequest {
  knowledgeBaseId: number
  model: string
  message: string
  conversationId?: string
}

export interface HistoryItem {
  role: 'user' | 'assistant'
  content: string
}

export interface SqlAskRequest {
  knowledgeBaseId: number
  model: string
  question: string
  history?: HistoryItem[]
}

export interface SqlAskResponse {
  status: 'SQL' | 'CLARIFY' | 'REJECTED'
  sql: string | null
  clarification: string | null
  options: string[] | null
  multiSelect: boolean | null
  isRejected: boolean
  canExecute: boolean
}

export interface VectorDbConfig {
  id: number
  dbType: string
  host: string
  port: number
  databaseName: string
  collectionName: string
  extraConfig: string
  active: boolean
  description: string
  createdAt: string
  updatedAt: string
}

export interface VectorDbConfigDTO {
  id?: number
  dbType: string
  host: string
  port: number
  databaseName: string
  collectionName: string
  extraConfig?: string
  active?: boolean
  description?: string
}

export interface AgentStep {
  type: 'think' | 'tool_call' | 'tool_result' | 'answer'
  content?: string
  tool?: string
  args?: Record<string, any>
  thought?: string
  result?: string
}

export type ThemeMode = 'obsidian' | 'parchment' | 'midnight'

// ===== 系统管理 =====

export interface MenuTreeNode {
  id: number
  parentId: number | null
  name: string
  path: string
  icon: string
  sort: number
  type: number
  children: MenuTreeNode[]
}

export interface RoleDTO {
  id: number
  name: string
  code: string
  status: number
  sort: number
  remark: string
  createdAt: string
}

export interface UserDTO {
  id: number
  username: string
  nickname: string
  email: string
  avatar: string
  status: number
  createdAt: string
  roles: RoleDTO[]
}

export interface MenuDTO {
  id: number
  parentId: number | null
  name: string
  path: string
  icon: string
  sort: number
  status: number
  type: number
}

export interface PageResult<T> {
  list: T[]
  total: number
}

// ===== 模型管理 =====

export interface ModelProviderDTO {
  id: number
  name: string
  logo: string
  code: string
  baseUrl: string
  apiKey: string
  description: string
  enabled: number
  sort: number
}

export interface CreateProviderRequest {
  name: string
  logo?: string
  code: string
  baseUrl: string
  apiKey?: string
  description?: string
  enabled?: number
  sort?: number
}

export interface ModelConfigDTO {
  id: number
  providerId: number
  providerName: string
  displayName: string
  modelCode: string
  modelType: string
  enabled: number
  sort: number
  supportTools: number
}

export interface CreateModelConfigRequest {
  providerId: number
  displayName: string
  modelCode: string
  modelType: string
  enabled?: number
  sort?: number
  supportTools?: number
}

// ===== Skill 技能管理 =====

export interface SkillDTO {
  id: number
  name: string
  displayName: string | null
  description: string
  content: string
  version: number
  status: 'DRAFT' | 'PENDING_APPROVAL' | 'ACTIVE' | 'REJECTED' | 'ARCHIVED'
  source: 'MANUAL' | 'AUTO_GENERATED'
  category: string | null
  tags: string | null
  qualityScore: number | null
  trialResult: string | null
  filePath: string | null
  parentSkillId: number | null
  authorUserId: number | null
  approvedBy: number | null
  approvedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface CreateSkillRequest {
  name: string
  displayName?: string
  description: string
  content: string
  category?: string
  tags?: string
  source?: 'MANUAL' | 'AUTO_GENERATED'
}

export interface GenerateSkillRequest {
  intent: string
}
