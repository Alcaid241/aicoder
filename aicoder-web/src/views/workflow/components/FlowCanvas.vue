<script setup lang="ts">
import { VueFlow, useVueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'
import StartNode from './nodes/StartNode.vue'
import EndNode from './nodes/EndNode.vue'
import LlmNode from './nodes/LlmNode.vue'
import RagNode from './nodes/RagNode.vue'
import Nl2SqlNode from './nodes/Nl2SqlNode.vue'
import ConditionNode from './nodes/ConditionNode.vue'
import ToolNode from './nodes/ToolNode.vue'
import SubWorkflowNode from './nodes/SubWorkflowNode.vue'
import LoopNode from './nodes/LoopNode.vue'
import { useWorkflowStore } from '@/stores/workflowStore'

const store = useWorkflowStore()
const {
  onNodeClick, onEdgeClick, onPaneClick, onConnect,
  addEdges, addNodes, removeNodes, removeEdges,
  project, vueFlowRef, getSelectedNodes, getSelectedEdges
} = useVueFlow()

const nodeTypes: Record<string, any> = {
  start: StartNode,
  end: EndNode,
  llm: LlmNode,
  rag: RagNode,
  nl2sql: Nl2SqlNode,
  condition: ConditionNode,
  tool: ToolNode,
  subworkflow: SubWorkflowNode,
  loop: LoopNode,
}

// 点击节点选中
onNodeClick(({ node }) => {
  if (node.type === 'start') {
    store.selectedNodeId = null
    return
  }
  if (node.type === 'end' && store.currentWorkflow?.type !== 'CHAT') {
    store.selectedNodeId = null
    return
  }
  store.selectedNodeId = node.id
})

// 点击边选中
onEdgeClick(() => {
  store.selectedNodeId = null
})

// 点击画布空白处取消选中
onPaneClick(() => {
  store.selectedNodeId = null
})

onConnect((params) => {
  addEdges([params])
})

// Delete / Backspace 键删除选中的节点或边
const onKeyDown = (event: KeyboardEvent) => {
  if (event.key === 'Delete' || event.key === 'Backspace') {
    // 如果焦点在 input/textarea/select 中则不处理
    const tag = (event.target as HTMLElement)?.tagName
    if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return

    const selectedNodes = getSelectedNodes.value
    const selectedEdges = getSelectedEdges.value

    if (selectedNodes.length > 0) {
      selectedNodes.forEach(n => store.removeNode(n.id))
      removeNodes(selectedNodes)
    }
    if (selectedEdges.length > 0) {
      selectedEdges.forEach(e => store.removeEdge(e.id))
      removeEdges(selectedEdges)
    }
  }
}

// 拖拽放置
const onDragOver = (event: DragEvent) => {
  event.preventDefault()
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'move'
  }
}

const onDrop = (event: DragEvent) => {
  const data = event.dataTransfer?.getData('application/vueflow')
  if (!data || !vueFlowRef.value) return

  const parsed = JSON.parse(data)
  const bounds = vueFlowRef.value.getBoundingClientRect()
  const position = project({
    x: event.clientX - bounds.left,
    y: event.clientY - bounds.top,
  })

  const labels: Record<string, string> = { llm: 'LLM 对话', rag: '知识检索', nl2sql: 'NL2SQL', condition: '条件分支', tool: 'HTTP 请求', subworkflow: '子工作流', loop: '循环' }

  addNodes([{
    id: `${parsed.type}_${Date.now()}`,
    type: parsed.type,
    position,
    data: {
      label: labels[parsed.type] || parsed.type,
      config: { ...parsed.defaultConfig }
    }
  }])
}
</script>

<template>
  <div class="flow-canvas" @keydown="onKeyDown" tabindex="0">
    <VueFlow
      v-model:nodes="store.nodes"
      v-model:edges="store.edges"
      :node-types="nodeTypes"
      :delete-key-code="'Delete'"
      fit-view-on-init
      @dragover="onDragOver"
      @drop="onDrop"
    >
      <Background />
      <Controls />
    </VueFlow>
  </div>
</template>

<style scoped lang="scss">
.flow-canvas {
  width: 100%;
  height: 100%;
  background: #fafafa;
  outline: none;
}
</style>
