import request from './request'
import type { ModelProviderDTO, CreateProviderRequest, ModelConfigDTO, CreateModelConfigRequest } from '@/types'

export const modelApi = {
  providerList: () => request.get<ModelProviderDTO[]>('/admin/model/provider/list'),
  providerCreate: (data: CreateProviderRequest) => request.post<ModelProviderDTO>('/admin/model/provider', data),
  providerUpdate: (id: number, data: CreateProviderRequest) => request.put<ModelProviderDTO>(`/admin/model/provider/${id}`, data),
  providerDelete: (id: number) => request.delete(`/admin/model/provider/${id}`),

  configList: (modelType?: string) => request.get<ModelConfigDTO[]>('/admin/model/config/list', { params: { modelType } }),
  configCreate: (data: CreateModelConfigRequest) => request.post<ModelConfigDTO>('/admin/model/config', data),
  configUpdate: (id: number, data: CreateModelConfigRequest) => request.put<ModelConfigDTO>(`/admin/model/config/${id}`, data),
  configDelete: (id: number) => request.delete(`/admin/model/config/${id}`)
}
