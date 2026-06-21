<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { api } from '../../api/index.js'
import { ElMessage } from 'element-plus'

const list = ref([]); const dialog = ref(false); const form = ref({})
const docDialog = ref(false); const currentKb = ref(null); const docs = ref([])
const retrieveQuery = ref(''); const retrieveResults = ref([])
const loading = ref(false)
let pollTimer = null
const DOC_POLL_INTERVAL_MS = 2000
const DOC_POLL_MAX_TICKS = 60   // stop polling after ~2min even if docs never settle

async function load() {
  loading.value = true
  try { list.value = (await api.adminKbs.list()) || [] } finally { loading.value = false }
}
function create() { form.value = {}; dialog.value = true }
async function save() { await api.adminKbs.create(form.value); dialog.value = false; ElMessage.success('已创建'); load() }
async function remove(row) { await api.adminKbs.remove(row.id); ElMessage.success('已删除'); load() }

async function openDocs(row) {
  currentKb.value = row; docDialog.value = true; retrieveResults.value = []; docs.value = []
  await refreshDocs()
  startPolling()
}
async function refreshDocs() {
  if (!currentKb.value) return
  docs.value = (await api.adminKbs.listDocs(currentKb.value.id)) || []
}
function startPolling() {
  stopPolling()
  let ticks = 0
  pollTimer = setInterval(async () => {
    ticks++
    try { await refreshDocs() } catch (_) { stopPolling(); return }
    const busy = docs.value.some((d) => d.status === 'pending' || d.status === 'processing')
    if (!busy || ticks >= DOC_POLL_MAX_TICKS) stopPolling()
  }, DOC_POLL_INTERVAL_MS)
}
function stopPolling() { if (pollTimer) { clearInterval(pollTimer); pollTimer = null } }

const uploadUrl = (id) => api.adminKbs.uploadUrl(id)
function beforeUpload(file) {
  const ok = /\.(txt|md)$/i.test(file.name)
  if (!ok) ElMessage.error('仅支持 .txt / .md 文档')
  return ok
}
function onUploaded() { ElMessage.success('已上传，正在处理'); refreshDocs(); startPolling() }
function onUploadError() { ElMessage.error('上传失败，请重试') }
async function runRetrieve() {
  if (!currentKb.value) return
  retrieveResults.value = (await api.adminKbs.retrieve(currentKb.value.id, retrieveQuery.value, 3)) || []
}
async function reprocessDoc(row) {
  try {
    await api.adminKbs.reprocessDoc(currentKb.value.id, row.id)
    ElMessage.success('已重新处理')
    refreshDocs(); startPolling()
  } catch (_) { /* interceptor 已提示 */ }
}
async function removeDoc(row) {
  try {
    await api.adminKbs.removeDoc(currentKb.value.id, row.id)
    ElMessage.success('已删除'); refreshDocs()
  } catch (_) { /* interceptor 已提示 */ }
}

watch(docDialog, (open) => { if (!open) stopPolling() })
onUnmounted(stopPolling)
onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head head-row">
      <div>
        <h2>知识库</h2>
        <p>建立知识库、上传文档，供 Agent 检索增强问答。</p>
      </div>
      <el-button type="primary" @click="create">新建知识库</el-button>
    </div>
    <el-table :data="list" v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="embeddingModel" label="向量模型" />
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button size="small" @click="openDocs(row)">文档/检索</el-button>
          <el-popconfirm title="删除知识库会一并清除文档与向量，确定？" width="240" confirm-button-text="删除" cancel-button-text="取消" @confirm="remove(row)">
            <template #reference><el-button size="small" type="danger">删除</el-button></template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" title="知识库" width="500">
      <el-form label-width="100px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="docDialog" :title="currentKb ? currentKb.name : ''" width="700">
      <el-upload
        :action="currentKb ? uploadUrl(currentKb.id) : ''" name="file"
        :before-upload="beforeUpload" :on-success="onUploaded" :on-error="onUploadError"
        :show-file-list="false"
      >
        <el-button type="primary">上传文档(txt/md)</el-button>
      </el-upload>
      <el-button style="margin-left:8px" @click="refreshDocs">刷新状态</el-button>
      <el-table :data="docs" style="margin-top:12px">
        <el-table-column prop="filename" label="文件" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag
              size="small" disable-transitions
              :type="row.status === 'done' ? 'success' : row.status === 'failed' ? 'danger' : 'info'"
            >{{ { pending: '待处理', processing: '处理中', done: '完成', failed: '失败', skipped: '未启用' }[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="chunkCount" label="切片数" width="70" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button v-if="row.status === 'failed' || row.status === 'skipped'" size="small" @click="reprocessDoc(row)">重试</el-button>
            <el-button size="small" type="danger" link @click="removeDoc(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-divider>检索测试</el-divider>
      <div style="display:flex;gap:8px">
        <el-input v-model="retrieveQuery" placeholder="输入查询" />
        <el-button @click="runRetrieve">检索</el-button>
      </div>
      <div v-for="(r, i) in retrieveResults" :key="i" style="font-size:12px;color:var(--ink-soft);margin-top:6px">{{ r.content }}</div>
    </el-dialog>
  </div>
</template>
