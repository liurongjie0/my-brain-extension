<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../../api/index.js'
import { ElMessage } from 'element-plus'

const list = ref([]); const dialog = ref(false); const form = ref({})
const testDialog = ref(false); const testArgs = ref('{}'); const testResult = ref(''); const testId = ref(null)

async function load() { list.value = await api.adminTools.list() }
function create() { form.value = { method: 'POST', enabled: true }; dialog.value = true }
function edit(row) { form.value = { ...row }; dialog.value = true }
async function save() {
  if (form.value.id) await api.adminTools.update(form.value.id, form.value)
  else await api.adminTools.create(form.value)
  dialog.value = false; ElMessage.success('已保存'); load()
}
async function remove(row) { await api.adminTools.remove(row.id); load() }
function openTest(row) { testId.value = row.id; testArgs.value = '{}'; testResult.value = ''; testDialog.value = true }
async function runTest() { testResult.value = await api.adminTools.test(testId.value, testArgs.value) }
onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head head-row">
      <div>
        <h2>工具</h2>
        <p>配置 HTTP 接口型工具，供 Agent 通过 Function Calling 调用。</p>
      </div>
      <el-button type="primary" @click="create">新建工具</el-button>
    </div>
    <el-table :data="list">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="method" label="方法" width="80" />
      <el-table-column prop="url" label="URL" />
      <el-table-column label="操作" width="220">
        <template #default="{ row }">
          <el-button size="small" @click="edit(row)">编辑</el-button>
          <el-button size="small" @click="openTest(row)">测试</el-button>
          <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" title="工具" width="600">
      <el-form label-width="120px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="描述(给模型看)"><el-input v-model="form.description" type="textarea" /></el-form-item>
        <el-form-item label="方法">
          <el-select v-model="form.method"><el-option label="POST" value="POST" /><el-option label="GET" value="GET" /></el-select>
        </el-form-item>
        <el-form-item label="URL"><el-input v-model="form.url" /></el-form-item>
        <el-form-item label="请求头(JSON)"><el-input v-model="form.headersJson" type="textarea" placeholder='{"Authorization":"Bearer x"}' /></el-form-item>
        <el-form-item label="参数Schema(JSON)"><el-input v-model="form.paramsSchemaJson" type="textarea" :rows="4" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="testDialog" title="工具测试" width="600">
      <el-input v-model="testArgs" type="textarea" :rows="3" placeholder='{"city":"上海"}' />
      <el-button type="primary" style="margin-top:8px" @click="runTest">执行</el-button>
      <pre style="background:#f5f5f5;padding:8px;margin-top:8px;white-space:pre-wrap">{{ testResult }}</pre>
    </el-dialog>
  </div>
</template>
