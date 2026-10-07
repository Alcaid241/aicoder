import request from './request'
import type { ChatRequest, ChatMessage, Conversation, ModelInfo } from '@/types'

export const chatApi = {
  send: (data: ChatRequest) => request.post('/chat/send', data),
  getConversations: () => request.get<Conversation[]>('/chat/conversations'),
  getMessages: (conversationId: number) => request.get<ChatMessage[]>(`/chat/conversations/${conversationId}/messages`),
  deleteConversation: (conversationId: number) => request.delete(`/chat/conversations/${conversationId}`),
  getModels: () => request.get<ModelInfo[]>('/chat/models')
}
