import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Conversation, ChatMessage, ModelInfo, AgentStep } from '@/types'
import { chatApi } from '@/api/chat'

export const useChatStore = defineStore('chat', () => {
  const conversations = ref<Conversation[]>([])
  const currentConversationId = ref<number | null>(null)
  const messages = ref<ChatMessage[]>([])
  const models = ref<ModelInfo[]>([])
  const currentModel = ref('')
  const streamingContent = ref('')
  const isStreaming = ref(false)
  const agentSteps = ref<AgentStep[]>([])
  const isAgentMode = ref(false)

  const fetchModels = async () => {
    const res = await chatApi.getModels()
    models.value = res.data
    if (models.value.length > 0 && !currentModel.value) {
      currentModel.value = models.value[0].modelId
    }
  }

  const fetchConversations = async () => {
    const res = await chatApi.getConversations()
    conversations.value = res.data
  }

  const fetchMessages = async (conversationId: number) => {
    currentConversationId.value = conversationId
    const res = await chatApi.getMessages(conversationId)
    messages.value = res.data.map(m => ({ ...m, role: m.role.toLowerCase() as 'user' | 'assistant' }))
  }

  const selectConversation = (id: number | null) => {
    currentConversationId.value = id
    if (id) {
      fetchMessages(id)
    } else {
      messages.value = []
    }
  }

  const createAndSend = async (model: string, message: string) => {
    messages.value.push({
      id: Date.now(),
      role: 'user',
      content: message,
      modelId: model,
      createdAt: new Date().toISOString()
    })
    streamingContent.value = ''
    isStreaming.value = true
  }

  const appendStreamContent = (chunk: string) => {
    streamingContent.value += chunk
  }

  const finishStreaming = () => {
    if (streamingContent.value) {
      messages.value.push({
        id: Date.now() + 1,
        role: 'assistant',
        content: streamingContent.value,
        modelId: currentModel.value,
        createdAt: new Date().toISOString()
      })
    }
    streamingContent.value = ''
    isStreaming.value = false
  }

  const deleteConversation = async (id: number) => {
    await chatApi.deleteConversation(id)
    conversations.value = conversations.value.filter(c => c.id !== id)
    if (currentConversationId.value === id) {
      currentConversationId.value = null
      messages.value = []
    }
  }

  const resetChat = () => {
    currentConversationId.value = null
    messages.value = []
    streamingContent.value = ''
    isStreaming.value = false
    agentSteps.value = []
    isAgentMode.value = false
  }

  const startAgentChat = async (model: string, message: string) => {
    messages.value.push({
      id: Date.now(),
      role: 'user',
      content: message,
      modelId: model,
      createdAt: new Date().toISOString()
    })
    streamingContent.value = ''
    isStreaming.value = true
    isAgentMode.value = true
    agentSteps.value = []
  }

  const addAgentStep = (step: AgentStep) => {
    agentSteps.value.push({ ...step })
  }

  const resetAgent = () => {
    agentSteps.value = []
    isAgentMode.value = false
  }

  return {
    conversations, currentConversationId, messages, models, currentModel,
    streamingContent, isStreaming, agentSteps, isAgentMode,
    fetchModels, fetchConversations, fetchMessages, selectConversation,
    createAndSend, appendStreamContent, finishStreaming, deleteConversation, resetChat,
    startAgentChat, addAgentStep, resetAgent
  }
})
