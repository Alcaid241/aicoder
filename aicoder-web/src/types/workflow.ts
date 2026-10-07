export interface WorkflowNodeConfig {
  // LLM
  model?: string
  promptTemplate?: string
  inputKey?: string
  outputKey?: string
  // RAG
  knowledgeBaseId?: string
  topK?: number
  // NL2SQL
  databaseName?: string
  // Condition
  conditions?: { label: string; match: string }[]
  // Tool (HTTP)
  url?: string
  method?: string
  headers?: Record<string, string>
  bodyTemplate?: string
  // SubWorkflow
  workflowId?: string
  // Loop
  maxIterations?: number
  exitCondition?: string
  // End (CHAT reply)
  replyRequirements?: string
}

export interface WorkflowNodeData {
  label: string
  config: WorkflowNodeConfig
}

export interface FlowNode {
  id: string
  type: string
  position: { x: number; y: number }
  data: WorkflowNodeData
}

export interface FlowEdge {
  id: string
  source: string
  target: string
  sourceHandle?: string
}

export interface GraphData {
  nodes: FlowNode[]
  edges: FlowEdge[]
}

export interface Workflow {
  id: number
  name: string
  description: string
  type: string
  replyRequirements?: string
  graphData: string
  status: string
  userId: number
  createdAt: string
  updatedAt: string
}

export interface WorkflowCreateRequest {
  name: string
  description?: string
  type?: string
  replyRequirements?: string
  graphData: string
}

export interface WorkflowExecutionRequest {
  input: Record<string, any>
}

export interface NodeExecutionEvent {
  executionId: number
  nodeId: string
  nodeName: string
  error?: string
  data?: string
}

export interface NodeExecInfo {
  nodeId: string
  nodeName: string
  status: 'running' | 'completed' | 'failed'
  startTime: number
  endTime?: number
  output?: string
  error?: string
}
