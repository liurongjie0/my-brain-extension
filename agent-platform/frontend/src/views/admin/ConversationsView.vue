<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../../api/index.js'

const list = ref([]); const dialog = ref(false); const msgs = ref([])
async function load() { list.value = await api.adminConversations.list() }
async function view(row) { msgs.value = await api.adminConversations.messages(row.id); dialog.value = true }
onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <h2>会话审计</h2>
      <p>查看全部会话与完整消息轨迹（含工具调用）。</p>
    </div>
    <el-table :data="list">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="title" label="标题" />
      <el-table-column prop="agentId" label="AgentId" width="100" />
      <el-table-column prop="updatedAt" label="更新时间" />
      <el-table-column label="操作" width="100">
        <template #default="{ row }"><el-button size="small" @click="view(row)">查看</el-button></template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="dialog" title="会话轨迹" width="700">
      <div v-for="(m, i) in msgs" :key="i" style="margin-bottom:10px">
        <el-tag size="small">{{ m.role }}</el-tag>
        <div style="white-space:pre-wrap;margin-top:4px">{{ m.content }}</div>
      </div>
    </el-dialog>
  </div>
</template>
