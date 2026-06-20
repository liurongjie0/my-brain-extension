<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../../api/index.js'
import { ElMessage } from 'element-plus'

const list = ref([]); const dialog = ref(false); const form = ref({})

async function load() { list.value = await api.adminModels.list() }
function create() { form.value = { enabled: true }; dialog.value = true }
function edit(row) { form.value = { ...row, apiKey: '' }; dialog.value = true }
async function save() {
  if (form.value.id) await api.adminModels.update(form.value.id, form.value)
  else await api.adminModels.create(form.value)
  dialog.value = false; ElMessage.success('已保存'); load()
}
async function remove(row) { await api.adminModels.remove(row.id); ElMessage.success('已删除'); load() }
onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head head-row">
      <div>
        <h2>模型管理</h2>
        <p>登记 OpenAI 兼容端点（独立 base-url / key / 模型名），Agent 可分别选用。</p>
      </div>
      <el-button type="primary" @click="create">新建模型</el-button>
    </div>
    <el-table :data="list">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="model" label="模型" />
      <el-table-column prop="baseUrl" label="Base URL" />
      <el-table-column prop="apiKeyMasked" label="Key" width="160" />
      <el-table-column prop="enabled" label="启用" width="80">
        <template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '是' : '否' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button size="small" @click="edit(row)">编辑</el-button>
          <el-popconfirm title="确定删除该模型？" confirm-button-text="删除" cancel-button-text="取消" @confirm="remove(row)">
            <template #reference><el-button size="small" type="danger">删除</el-button></template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" title="模型配置" width="560">
      <el-form label-width="100px">
        <el-form-item label="名称"><el-input v-model="form.name" placeholder="如 DeepSeek-Chat" /></el-form-item>
        <el-form-item label="Base URL"><el-input v-model="form.baseUrl" placeholder="https://api.deepseek.com" /></el-form-item>
        <el-form-item label="API Key">
          <el-input v-model="form.apiKey" :placeholder="form.id ? '留空则不修改' : 'sk-...'" show-password />
        </el-form-item>
        <el-form-item label="模型名"><el-input v-model="form.model" placeholder="deepseek-chat" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
    </el-dialog>
  </div>
</template>
