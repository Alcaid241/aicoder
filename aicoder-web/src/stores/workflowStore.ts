import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Workflow, GraphData, FlowNode, FlowEdge, NodeExecInfo } from '@/types/workflow'
import { workflowApi } from '@/api/workflow'

export const useWorkflowStore = defineStore('workflow', () => {
  const workflows = ref<Workflow[]>([])
  const currentWorkflow = ref<Workflow | null>(null)
  const nodes = ref<FlowNode[]>([])
  const edges = ref<FlowEdge[]>([])
  const selectedNodeId = ref<string | null>(null)
  const executing = ref(false)
  const nodeExecList = ref<NodeExecInfo[]>([])
  const executionOutput = ref('')
  const executionError = ref('')

  // 节点状态 Map（供画布节点组件读取状态变色）
  const nodeStatusMap = computed(() => {
    const map: Record<string, string> = {}
    for (const item of nodeExecList.value) {
      map[item.nodeId] = item.status
    }
    return map
  })

  const fetchList = async () => {
    const res = await workflowApi.list()
    workflows.value = res.data
  }

  const fetchWorkflow = async (id: number) => {
    const res = await workflowApi.getById(id)
    currentWorkflow.value = res.data
    const graphData: GraphData = JSON.parse(res.data.graphData || '{"nodes":[],"edges":[]}')
    nodes.value = graphData.nodes || []
    edges.value = graphData.edges || []
    // 将 replyRequirements 回填到结束节点 config
    if (currentWorkflow.value.replyRequirements) {
      const endNode = nodes.value.find(n => n.type === 'end')
      if (endNode) {
        endNode.data = {
          ...endNode.data,
          config: { ...endNode.data?.config, replyRequirements: currentWorkflow.value!.replyRequirements }
        }
      }
    }
  }

  const saveWorkflow = async () => {
    if (!currentWorkflow.value) return
    const graphData: GraphData = { nodes: nodes.value, edges: edges.value }
    // 从结束节点提取回复要求
    const endNode = nodes.value.find(n => n.type === 'end')
    const replyReq = endNode?.data?.config?.replyRequirements || ''
    await workflowApi.update(currentWorkflow.value.id, {
      name: currentWorkflow.value.name,
      description: currentWorkflow.value.description,
      type: currentWorkflow.value.type,
      replyRequirements: replyReq,
      graphData: JSON.stringify(graphData)
    })
  }

  const executeWorkflow = async (input: Record<string, any>) => {
    if (!currentWorkflow.value) return
    executing.value = true
    nodeExecList.value = []
    executionOutput.value = ''
    executionError.value = ''

    try {
      const response = await workflowApi.execute(currentWorkflow.value.id, { input })

      if (!response.ok) {
        const errorText = await response.text()
        executionError.value = errorText || `HTTP ${response.status}`
        return
      }

      const reader = response.body?.getReader()
      const decoder = new TextDecoder()

      if (!reader) {
        executionError.value = '无法读取响应流'
        return
      }

      let buffer = ''
      let currentEvent = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          if (line.startsWith('event:')) {
            currentEvent = line.slice(6).trim()
          } else if (line.startsWith('data:')) {
            const data = line.slice(5).trim()
            if (currentEvent) {
              handleSSEEvent(currentEvent, data)
              currentEvent = ''
            }
          }
        }
      }

      if (buffer.startsWith('data:') && currentEvent) {
        handleSSEEvent(currentEvent, buffer.slice(5).trim())
      }
    } catch (e: any) {
      executionError.value = e.message || '网络错误'
    } finally {
      executing.value = false
    }
  }

  const handleSSEEvent = (event: string, data: string) => {
    try {
      const payload = JSON.parse(data)

      if (event === 'node_start') {
        nodeExecList.value.push({
          nodeId: payload.nodeId,
          nodeName: payload.nodeName || payload.nodeId,
          status: 'running',
          startTime: Date.now()
        })
      } else if (event === 'node_complete') {
        const item = nodeExecList.value.find(n => n.nodeId === payload.nodeId && n.status === 'running')
        if (item) {
          item.status = 'completed'
          item.endTime = Date.now()
          item.output = payload.data
        }
      } else if (event === 'node_error') {
        const item = nodeExecList.value.find(n => n.nodeId === payload.nodeId && n.status === 'running')
        if (item) {
          item.status = 'failed'
          item.endTime = Date.now()
          item.error = payload.error
        }
      } else if (event === 'workflow_complete') {
        try {
          const state = JSON.parse(data)
          // 提取最终输出：取最后一个有意义的 key 的值
          const keys = Object.keys(state).filter(k => k !== 'query')
          if (keys.length > 0) {
            const lastKey = keys[keys.length - 1]
            const val = state[lastKey]
            executionOutput.value = typeof val === 'string' ? val : JSON.stringify(val, null, 2)
          }
        } catch {
          executionOutput.value = data
        }
      } else if (event === 'workflow_error') {
        executionError.value = payload.error || '未知错误'
      }
    } catch (e) {
      // ignore parse errors
    }
  }

  const selectedNode = () => {
    if (!selectedNodeId.value) return null
    return nodes.value.find(n => n.id === selectedNodeId.value) || null
  }

  const updateNodeConfig = (nodeId: string, config: Record<string, any>) => {
    const node = nodes.value.find(n => n.id === nodeId)
    if (node) {
      node.data.config = { ...node.data.config, ...config }
    }
  }

  const updateNodeData = (nodeId: string, data: any) => {
    const node = nodes.value.find(n => n.id === nodeId)
    if (node) {
      node.data = { ...data }
    }
  }

  const removeNode = (nodeId: string) => {
    nodes.value = nodes.value.filter(n => n.id !== nodeId)
    edges.value = edges.value.filter(e => e.source !== nodeId && e.target !== nodeId)
    if (selectedNodeId.value === nodeId) selectedNodeId.value = null
  }

  const removeEdge = (edgeId: string) => {
    edges.value = edges.value.filter(e => e.id !== edgeId)
  }

  return {
    workflows, currentWorkflow, nodes, edges, selectedNodeId,
    executing, nodeStatusMap, nodeExecList, executionOutput, executionError,
    fetchList, fetchWorkflow, saveWorkflow, executeWorkflow,
    selectedNode, updateNodeConfig, updateNodeData, removeNode, removeEdge
  }
})
