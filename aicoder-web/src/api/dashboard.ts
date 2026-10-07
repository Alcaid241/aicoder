import request from './request'

export interface DashboardStats {
  conversationCount: number
  knowledgeBaseCount: number
  workflowCount: number
  executionCount: number
}

export const dashboardApi = {
  getStats: () => request.get<DashboardStats>('/admin/dashboard/stats')
}
