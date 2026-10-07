import request from './request'
import type { SqlAskRequest, SqlAskResponse } from '@/types'

export const sqlApi = {
  ask: (data: SqlAskRequest) => request.post<SqlAskResponse>('/rag/sql/ask', data),
  execute: (sql: string, databaseName: string) => request.post('/rag/sql/execute', { sql, databaseName })
}
