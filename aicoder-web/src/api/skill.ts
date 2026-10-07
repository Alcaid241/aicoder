import request from './request'
import type { SkillDTO, CreateSkillRequest, GenerateSkillRequest } from '@/types'

export const skillApi = {
  list: () => request.get<SkillDTO[]>('/skill'),
  pending: () => request.get<SkillDTO[]>('/skill/pending'),
  getById: (id: number) => request.get<SkillDTO>(`/skill/${id}`),
  create: (data: CreateSkillRequest) => request.post<SkillDTO>('/skill', data),
  update: (id: number, data: CreateSkillRequest) => request.put<SkillDTO>(`/skill/${id}`, data),
  submit: (id: number) => request.put<SkillDTO>(`/skill/${id}/submit`),
  approve: (id: number) => request.put<SkillDTO>(`/skill/${id}/approve`),
  reject: (id: number) => request.put<SkillDTO>(`/skill/${id}/reject`),
  archive: (id: number) => request.put<SkillDTO>(`/skill/${id}/archive`),
  // LLM 调用耗时较长，单独放宽超时到 120s（request.ts 默认 30s）
  generate: (data: GenerateSkillRequest) =>
    request.post<SkillDTO>('/skill/generate', data, { timeout: 120000 })
}
