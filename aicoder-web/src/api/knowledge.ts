import request from './request'
import type { KnowledgeBase, KnowledgeBaseCreateRequest, KnowledgeBaseUpdateRequest } from '@/types'

export const knowledgeApi = {
  list: () => request.get<KnowledgeBase[]>('/rag/kb'),
  create: (data: KnowledgeBaseCreateRequest) => request.post<KnowledgeBase>('/rag/kb', data),
  update: (id: number, data: KnowledgeBaseUpdateRequest) => request.put<KnowledgeBase>(`/rag/kb/${id}`, data),
  upload: (id: number, file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return request.post(`/rag/kb/${id}/upload`, formData, {
      timeout: 120000
    })
  },
  delete: (id: number) => request.delete(`/rag/kb/${id}`),
  getDocuments: (id: number) => request.get<KnowledgeDocument[]>('/rag/kb/' + id + '/documents'),
  deleteDocument: (kbId: number, docId: number) => request.delete(`/rag/kb/${kbId}/docs/${docId}`),
  downloadDocument: (kbId: number, docId: number) => {
    const token = localStorage.getItem('token')
    const url = `/api/rag/kb/${kbId}/docs/${docId}/download`
    return fetch(url, {
      headers: { 'Authorization': `Bearer ${token}` }
    })
  }
}

export interface KnowledgeDocument {
  id: number
  knowledgeBaseId: number
  fileName: string
  fileType: string
  fileSize: number
  chunkCount: number
  status: string
  errorMessage: string
  createdAt: string
}
