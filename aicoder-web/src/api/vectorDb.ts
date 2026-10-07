import request from './request'
import type { VectorDbConfig, VectorDbConfigDTO } from '@/types'

export const vectorDbApi = {
  list: () => request.get<VectorDbConfig[]>('/admin/vector-db-config'),
  create: (data: VectorDbConfigDTO) => request.post<VectorDbConfig>('/admin/vector-db-config', data),
  update: (id: number, data: VectorDbConfigDTO) => request.put<VectorDbConfig>(`/admin/vector-db-config/${id}`, data),
  delete: (id: number) => request.delete(`/admin/vector-db-config/${id}`),
  activate: (id: number) => request.put<VectorDbConfig>(`/admin/vector-db-config/${id}/activate`)
}
