<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../../api/index.js'

const data = ref({ overview: {}, tools: [], agents: [] })
const loading = ref(false)

async function load() {
  loading.value = true
  try { data.value = await api.metrics() } finally { loading.value = false }
}
function rateType(r) { return r >= 95 ? 'success' : r >= 80 ? 'warning' : 'danger' }
onMounted(load)
</script>

<template>
  <div class="page" v-loading="loading">
    <div class="page-head head-row">
      <div>
        <h2>监控</h2>
        <p>工具调用成功率、各 Agent 调用次数与平台总览。</p>
      </div>
      <el-button @click="load">刷新</el-button>
    </div>

    <div class="cards">
      <div class="stat"><div class="k">Agent</div><div class="v">{{ data.overview.agents || 0 }}<span class="u">/ {{ data.overview.enabledAgents || 0 }} 启用</span></div></div>
      <div class="stat"><div class="k">会话</div><div class="v">{{ data.overview.conversations || 0 }}</div></div>
      <div class="stat"><div class="k">消息</div><div class="v">{{ data.overview.messages || 0 }}</div></div>
      <div class="stat"><div class="k">工具调用</div><div class="v">{{ data.overview.toolCalls || 0 }}</div></div>
      <div class="stat highlight">
        <div class="k">工具整体成功率</div>
        <div class="v">{{ (data.overview.toolSuccessRate || 0) }}<span class="u">%</span></div>
      </div>
    </div>

    <h3 class="section">各工具成功率</h3>
    <el-table :data="data.tools">
      <el-table-column prop="toolName" label="工具" />
      <el-table-column prop="total" label="调用次数" width="120" />
      <el-table-column prop="success" label="成功" width="100" />
      <el-table-column label="成功率" width="220">
        <template #default="{ row }">
          <el-progress :percentage="row.successRate" :status="rateType(row.successRate) === 'success' ? 'success' : rateType(row.successRate) === 'danger' ? 'exception' : ''" />
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!data.tools.length" description="暂无工具调用记录" :image-size="80" />

    <h3 class="section">各 Agent 调用次数</h3>
    <el-table :data="data.agents">
      <el-table-column prop="agentId" label="ID" width="60" />
      <el-table-column prop="agentName" label="Agent" />
      <el-table-column prop="conversations" label="会话数" width="120" />
      <el-table-column prop="calls" label="调用次数(用户轮)" width="160" />
    </el-table>
  </div>
</template>

<style scoped>
.cards { display: flex; flex-wrap: wrap; gap: 14px; margin-bottom: 8px; }
.stat {
  flex: 1; min-width: 150px;
  background: var(--surface); border: 1px solid var(--line);
  border-radius: 14px; padding: 16px 18px;
  box-shadow: 0 1px 2px rgba(60, 50, 38, 0.04);
}
.stat.highlight { border-color: var(--clay); background: var(--clay-tint); }
.stat .k { font-size: 12.5px; color: var(--muted); margin-bottom: 8px; }
.stat .v { font-size: 26px; font-weight: 650; color: var(--ink); }
.stat .u { font-size: 12.5px; color: var(--muted); font-weight: 400; margin-left: 6px; }
.section { margin: 26px 0 10px; font-size: 15px; font-weight: 600; color: var(--ink); }
</style>
