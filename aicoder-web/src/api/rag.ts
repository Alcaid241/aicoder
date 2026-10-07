import { streamRequest } from './request'

export const ragChatApi = {
  streamRagChat: (knowledgeBaseId: number, model: string, message: string,
    onEvent: (eventType: string, data: string) => void,
    onError?: (error: Error) => void,
    onComplete?: () => void
  ) => {
    return streamRequest('/api/rag/chat/stream', {
      knowledgeBaseId,
      model,
      message
    }, { onEvent, onError, onComplete })
  }
}
