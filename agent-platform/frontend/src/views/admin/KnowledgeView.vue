<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../../api/index.js'
import { ElMessage } from 'element-plus'

const list = ref([]); const dialog = ref(false); const form = ref({})
const docDialog = ref(false); const currentKb = ref(null); const docs = ref([])
const retrieveQuery = ref(''); const retrieveResults = ref([])

async function load() { list.value = await api.adminKbs.list() }
function create() { form.value = {}; dialog.value = true }
async function save() { await api.adminKbs.create(form.value); dialog.value = false; ElMessage.success('已创建'); load() }
async function remove(row) { await api.adminKbs.remove(row.id); load() }
async function openDocs(row) {
  currentKb.value = row; docDialog.value = true; retrieveResults.value = []
  docs.value = await api.adminKbs.listDocs(row.id)
}
async function refreshDocs() { docs.value = await api.adminKbs.listDocs(currentKb.value.id) }
const uploadUrl = (id) => api.adminKbs.uploadUrl(id)
function onUploaded() { ElMessage.success('已上传，正在处理'); setTimeout(refreshDocs, 1500) }
async function runRetrieve() {
  retrieveResults.value = await api.adminKbs.retrieve(currentKb.value.id, retrieveQuery.value, 3)
}
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
    <el-table :data="list">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="embeddingModel" label="向量模型" />
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button size="small" @click="openDocs(row)">文档/检索</el-button>
          <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
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
      <el-upload :action="uploadUrl(currentKb.id)" name="file" :on-success="onUploaded" :show-file-list="false">
        <el-button type="primary">上传文档(txt/md)</el-button>
      </el-upload>
      <el-button style="margin-left:8px" @click="refreshDocs">刷新状态</el-button>
      <el-table :data="docs" style="margin-top:12px">
        <el-table-column prop="filename" label="文件" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="chunkCount" label="切片数" width="80" />
      </el-table>
      <el-divider>检索测试</el-divider>
      <div style="display:flex;gap:8px">
        <el-input v-model="retrieveQuery" placeholder="输入查询" />
        <el-button @click="runRetrieve">检索</el-button>
      </div>
      <div v-for="(r, i) in retrieveResults" :key="i" style="font-size:12px;color:#666;margin-top:6px">{{ r.content }}</div>
    </el-dialog>
  </div>
</template>
