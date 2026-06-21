<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../../api/index.js'
import { renderMarkdown } from '../../utils/markdown.js'
import ToolTrace from '../../components/ToolTrace.vue'

const list = ref([]); const dialog = ref(false); const msgs = ref([])
const loading = ref(false)
const roleLabel = { user: '用户', assistant: '助手', system: '系统' }
function parseSteps(toolCalls) {
  if (!toolCalls) return []
  try { const a = JSON.parse(toolCalls); return Array.isArray(a) ? a : [] } catch (_) { return [] }
}
async function load() {
  loading.value = true
  try { list.value = (await api.adminConversations.list()) || [] } finally { loading.value = false }
}
async function view(row) {
  // pre-parse the trajectory once per message instead of re-parsing in the template
  const data = (await api.adminConversations.messages(row.id)) || []
  msgs.value = data.map((m) => ({ ...m, steps: parseSteps(m.toolCalls) }))
  dialog.value = true
}
onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <h2>会话审计</h2>
      <p>查看全部会话与完整消息轨迹（含工具调用）。</p>
    </div>
    <el-table :data="list" v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="title" label="标题" show-overflow-tooltip />
      <el-table-column prop="agentId" label="AgentId" width="100" />
      <el-table-column prop="updatedAt" label="更新时间" min-width="160" />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }"><el-button size="small" @click="view(row)">查看</el-button></template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="dialog" title="会话轨迹" width="700">
      <div v-for="(m, i) in msgs" :key="i" style="margin-bottom:12px">
        <el-tag size="small">{{ roleLabel[m.role] || m.role }}</el-tag>
        <ToolTrace
          v-if="m.role === 'assistant' && m.steps.length"
          :steps="m.steps" style="margin-top:6px"
        />
        <div v-if="m.role === 'assistant'" class="md-body" style="margin-top:6px" v-html="renderMarkdown(m.content)"></div>
        <div v-else style="white-space:pre-wrap;margin-top:4px">{{ m.content }}</div>
      </div>
    </el-dialog>
  </div>
</template>
