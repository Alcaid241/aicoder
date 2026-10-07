import request from './request'
import type { Workflow, WorkflowCreateRequest, WorkflowExecutionRequest } from '@/types/workflow'

export const workflowApi = {
  list: () => request.get<Workflow[]>('/workflow/list'),
  getById: (id: number) => request.get<Workflow>(`/workflow/${id}`),
  create: (data: WorkflowCreateRequest) => request.post<Workflow>('/workflow/create', data),
  update: (id: number, data: WorkflowCreateRequest) => request.put<Workflow>(`/workflow/${id}`, data),
  delete: (id: number) => request.delete(`/workflow/${id}`),
  execute: (id: number, data: WorkflowExecutionRequest) =>
    fetch(`/api/workflow/${id}/execute`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      },
      body: JSON.stringify(data)
    }),
  getExecutions: (workflowId: number) => request.get(`/workflow/${workflowId}/executions`),
  getExecutionDetail: (executionId: number) => request.get(`/workflow/executions/${executionId}`),
  getNodeExecutions: (executionId: number) => request.get(`/workflow/executions/${executionId}/nodes`),
  // 模板
  listTemplates: () => request.get<any[]>('/workflow/templates'),
  createTemplate: (data: { name: string; description?: string; category?: string; graphData: string }) =>
    request.post('/workflow/templates', data),
  cloneFromTemplate: (templateId: number) =>
    request.post<any>(`/workflow/templates/${templateId}/clone`),
  deleteTemplate: (templateId: number) =>
    request.delete(`/workflow/templates/${templateId}`),
}
