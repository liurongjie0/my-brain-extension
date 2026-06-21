<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../../api/index.js'
import { ElMessage } from 'element-plus'

const list = ref([]); const dialog = ref(false); const form = ref({})
const testDialog = ref(false); const testTools = ref([]); const testName = ref('')
const loading = ref(false); const testing = ref(false)

async function load() {
  loading.value = true
  try { list.value = (await api.adminMcp.list()) || [] } finally { loading.value = false }
}
function create() { form.value = { transport: 'sse', enabled: true }; dialog.value = true }
function edit(row) { form.value = { ...row }; dialog.value = true }
async function save() {
  if (form.value.id) await api.adminMcp.update(form.value.id, form.value)
  else await api.adminMcp.create(form.value)
  dialog.value = false; ElMessage.success('已保存'); load()
}
async function remove(row) { await api.adminMcp.remove(row.id); ElMessage.success('已删除'); load() }
async function test(row) {
  testName.value = row.name; testTools.value = []; testDialog.value = true; testing.value = true
  try {
    testTools.value = (await api.adminMcp.test(row.id)) || []
    ElMessage.success(`连接成功，发现 ${testTools.value.length} 个工具`)
  } catch (e) { /* 错误已由拦截器提示 */ } finally { testing.value = false }
}
onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head head-row">
      <div>
        <h2>MCP 管理</h2>
        <p>登记 MCP server（当前支持 SSE），连接拉取其工具；在 Agent「绑定」里挂给 Agent 调用。</p>
      </div>
      <el-button type="primary" @click="create">新建 MCP</el-button>
    </div>
    <el-table :data="list" v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="transport" label="传输" width="90" />
      <el-table-column prop="url" label="URL" />
      <el-table-column prop="enabled" label="启用" width="80">
        <template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'" disable-transitions>{{ row.enabled ? '是' : '否' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" width="240">
        <template #default="{ row }">
          <el-button size="small" @click="edit(row)">编辑</el-button>
          <el-button size="small" @click="test(row)">连接测试</el-button>
          <el-popconfirm title="确定删除该 MCP？" confirm-button-text="删除" cancel-button-text="取消" @confirm="remove(row)">
            <template #reference><el-button size="small" type="danger">删除</el-button></template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" title="MCP server" width="560">
      <el-form label-width="100px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="传输">
          <el-select v-model="form.transport">
            <el-option label="SSE" value="sse" />
            <el-option label="stdio（待支持）" value="stdio" disabled />
          </el-select>
        </el-form-item>
        <el-form-item label="URL">
          <el-input v-model="form.url" placeholder="http://localhost:8765（填 base，自动补 /sse）" />
        </el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="testDialog" :title="`连接测试 · ${testName}`" width="500">
      <div v-loading="testing" :element-loading-text="testing ? '连接中…' : ''" style="min-height:72px">
        <div v-if="testTools.length">
          <div style="margin-bottom:8px;color:var(--muted);font-size:13px">该 MCP 暴露的工具：</div>
          <el-tag v-for="t in testTools" :key="t" style="margin:0 6px 6px 0" disable-transitions>{{ t }}</el-tag>
        </div>
        <el-empty v-else-if="!testing" description="未发现工具或连接失败" :image-size="80" />
      </div>
    </el-dialog>
  </div>
</template>
