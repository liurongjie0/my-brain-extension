<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../../api/index.js'
import { ElMessage } from 'element-plus'

const list = ref([]); const kbs = ref([]); const tools = ref([]); const models = ref([]); const mcps = ref([])
const dialog = ref(false); const bindDialog = ref(false)
const form = ref({}); const bindForm = ref({ id: null, kbIds: [], toolIds: [], mcpIds: [] })
const loading = ref(false); const saving = ref(false)

async function load() {
  loading.value = true
  try {
    const [l, k, t, m, mc] = await Promise.all([
      api.adminAgents.list(), api.adminKbs.list(), api.adminTools.list(),
      api.adminModels.list(), api.adminMcp.list()
    ])
    list.value = l || []; kbs.value = k || []; tools.value = t || []; models.value = m || []; mcps.value = mc || []
  } finally { loading.value = false }
}
function create() { form.value = { agentType: 'chat', temperature: 0.7, maxTokens: 2048, topP: 1.0, enabled: true, planEnabled: false }; dialog.value = true }
function edit(row) { form.value = { ...row }; dialog.value = true }
async function save() {
  saving.value = true
  try {
    if (form.value.id) await api.adminAgents.update(form.value.id, form.value)
    else await api.adminAgents.create(form.value)
    dialog.value = false; ElMessage.success('已保存'); load()
  } catch (_) { /* interceptor 已提示 */ } finally { saving.value = false }
}
async function remove(row) { await api.adminAgents.remove(row.id); ElMessage.success('已删除'); load() }
async function openBind(row) {
  const b = await api.adminAgents.getBindings(row.id)
  bindForm.value = { id: row.id, kbIds: b.kbIds || [], toolIds: b.toolIds || [], mcpIds: b.mcpIds || [] }
  bindDialog.value = true
}
async function saveBind() {
  saving.value = true
  try {
    await api.adminAgents.bindings(bindForm.value.id, {
      kbIds: bindForm.value.kbIds, toolIds: bindForm.value.toolIds, mcpIds: bindForm.value.mcpIds
    })
    bindDialog.value = false; ElMessage.success('绑定已更新')
  } catch (_) { /* interceptor 已提示 */ } finally { saving.value = false }
}
onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head head-row">
      <div>
        <h2>Agent 管理</h2>
        <p>配置提示词、模型与类型，绑定知识库和工具。</p>
      </div>
      <el-button type="primary" @click="create">新建 Agent</el-button>
    </div>
    <el-table :data="list" v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="agentType" label="类型" width="90" />
      <el-table-column prop="model" label="模型" />
      <el-table-column prop="enabled" label="启用" width="80">
        <template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '是' : '否' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" width="220">
        <template #default="{ row }">
          <el-button size="small" @click="edit(row)">编辑</el-button>
          <el-button size="small" @click="openBind(row)">绑定</el-button>
          <el-popconfirm title="确定删除该 Agent？" confirm-button-text="删除" cancel-button-text="取消" @confirm="remove(row)">
            <template #reference><el-button size="small" type="danger">删除</el-button></template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" title="Agent" width="600">
      <el-form label-width="100px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" /></el-form-item>
        <el-form-item label="系统提示词"><el-input v-model="form.systemPrompt" type="textarea" :rows="4" /></el-form-item>
        <el-form-item label="模型端点">
          <el-select v-model="form.modelConfigId" clearable placeholder="选已登记的模型(留空用全局默认)" style="width:100%">
            <el-option v-for="m in models" :key="m.id" :label="`${m.name} (${m.model})`" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="模型名">
          <el-input v-model="form.model" placeholder="未选端点时用，如 gpt-4o-mini" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.agentType">
            <el-option label="纯对话 chat" value="chat" />
            <el-option label="知识问答 rag" value="rag" />
            <el-option label="工具 tool" value="tool" />
            <el-option label="多步骤 react" value="react" />
          </el-select>
        </el-form-item>
        <el-form-item label="temperature"><el-input-number v-model="form.temperature" :step="0.1" :min="0" :max="2" /></el-form-item>
        <el-form-item label="maxTokens"><el-input-number v-model="form.maxTokens" :step="128" :min="1" /></el-form-item>
        <el-form-item v-if="form.agentType === 'react'" label="任务清单">
          <el-switch v-model="form.planEnabled" />
          <span style="margin-left:10px;font-size:12px;color:var(--muted)">让 react Agent 用 write_todos 边做边维护可见的步骤清单</span>
        </el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="bindDialog" title="绑定知识库/工具" width="500">
      <el-form label-width="80px">
        <el-form-item label="知识库">
          <el-select v-model="bindForm.kbIds" multiple style="width:100%">
            <el-option v-for="k in kbs" :key="k.id" :label="k.name" :value="k.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="工具">
          <el-select v-model="bindForm.toolIds" multiple style="width:100%">
            <el-option v-for="t in tools" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="MCP">
          <el-select v-model="bindForm.mcpIds" multiple style="width:100%">
            <el-option v-for="m in mcps" :key="m.id" :label="m.name" :value="m.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="bindDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveBind">保存</el-button></template>
    </el-dialog>
  </div>
</template>
