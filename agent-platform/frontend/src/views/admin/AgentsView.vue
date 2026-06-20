<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../../api/index.js'
import { ElMessage } from 'element-plus'

const list = ref([]); const kbs = ref([]); const tools = ref([])
const dialog = ref(false); const bindDialog = ref(false)
const form = ref({}); const bindForm = ref({ id: null, kbIds: [], toolIds: [] })

async function load() {
  list.value = await api.adminAgents.list()
  kbs.value = await api.adminKbs.list()
  tools.value = await api.adminTools.list()
}
function create() { form.value = { agentType: 'chat', temperature: 0.7, maxTokens: 2048, topP: 1.0, enabled: true }; dialog.value = true }
function edit(row) { form.value = { ...row }; dialog.value = true }
async function save() {
  if (form.value.id) await api.adminAgents.update(form.value.id, form.value)
  else await api.adminAgents.create(form.value)
  dialog.value = false; ElMessage.success('已保存'); load()
}
async function remove(row) { await api.adminAgents.remove(row.id); load() }
function openBind(row) { bindForm.value = { id: row.id, kbIds: [], toolIds: [] }; bindDialog.value = true }
async function saveBind() {
  await api.adminAgents.bindings(bindForm.value.id, { kbIds: bindForm.value.kbIds, toolIds: bindForm.value.toolIds })
  bindDialog.value = false; ElMessage.success('绑定已更新')
}
onMounted(load)
</script>

<template>
  <div style="padding:16px">
    <el-button type="primary" @click="create">新建 Agent</el-button>
    <el-table :data="list" style="margin-top:12px">
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
          <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" title="Agent" width="600">
      <el-form label-width="100px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" /></el-form-item>
        <el-form-item label="系统提示词"><el-input v-model="form.systemPrompt" type="textarea" :rows="4" /></el-form-item>
        <el-form-item label="模型"><el-input v-model="form.model" placeholder="gpt-4o-mini" /></el-form-item>
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
        <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
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
      </el-form>
      <template #footer><el-button @click="bindDialog = false">取消</el-button><el-button type="primary" @click="saveBind">保存</el-button></template>
    </el-dialog>
  </div>
</template>
