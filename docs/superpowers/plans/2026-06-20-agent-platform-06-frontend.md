# Agent 平台 · 计划 06：前端（Vue3 + Element Plus）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans。Steps 用 `- [ ]`。

**Goal:** 用户对话端（选 Agent、SSE 流式对话、ReAct 步骤折叠、RAG 引用）+ 管理后台（Agent / 知识库 / 工具 / 会话审计）。

**Architecture:** Vite + Vue3 + Element Plus + Vue Router + Pinia + axios。SSE 用 `fetch` 流式读取。开发期 Vite 代理 `/api` 到后端 `:8080`。先补一个后端 admin 会话审计端点。

**Tech Stack:** Node 25 / npm 11；Vue 3、Element Plus、vue-router、pinia、axios、Vitest（少量单测）。

## Global Constraints
- 前端目录 `agent-platform/frontend/`
- 注释/命名英文，文案中文；与后端 `ApiResponse{code,message,data}` 约定一致
- 主要验证：`npm run build` 通过；关键工具函数有单测

## 文件结构
```
agent-platform/frontend/
├── package.json / vite.config.js / index.html
├── vitest.config.js
└── src/
    ├── main.js / App.vue / router/index.js
    ├── api/http.js          # axios 实例 + ApiResponse 解包
    ├── api/sse.js           # POST + SSE 流式读取 (fetch) + parseSseStream 工具
    ├── api/index.js         # 各接口封装
    ├── stores/user.js       # 匿名 userId (localStorage)
    ├── layouts/MainLayout.vue
    ├── views/chat/ChatView.vue
    └── views/admin/{AgentsView,KnowledgeView,ToolsView,ConversationsView}.vue
agent-platform/backend/.../chat/  # 补 admin 会话审计端点
```

---

### Task 0: 后端补 admin 会话审计端点

**Files:** Modify `chat/ConversationRepository.java`、`chat/ChatController.java`；Test: `chat/AdminConversationTest.java`

**Interfaces:**
- `ConversationRepository.findAllByOrderByUpdatedAtDesc()`。
- `GET /api/admin/conversations` → `ApiResponse<List<ConversationResponse>>`（全部）；`GET /api/admin/conversations/{id}/messages` 复用已有消息查询（新增 admin 路径）。

- [ ] **Step 1: 失败测试 AdminConversationTest.java**
```java
package com.agentplatform.chat;

import com.agentplatform.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class AdminConversationTest extends IntegrationTestBase {
    @Autowired MockMvc mvc;
    @Autowired ConversationRepository conversations;
    @Autowired com.agentplatform.agent.AgentRepository agents;

    @Test
    void admin_lists_all_conversations() throws Exception {
        var a = new com.agentplatform.agent.AgentEntity();
        a.setName("x"); a.setModel("gpt-4o-mini"); a.setAgentType("chat");
        a.setTemperature(0.7); a.setMaxTokens(256); a.setTopP(1.0); a.setEnabled(true);
        Long agentId = agents.save(a).getId();
        ConversationEntity c = new ConversationEntity();
        c.setAgentId(agentId); c.setUserId("u-9"); c.setTitle("审计会话");
        conversations.save(c);

        mvc.perform(get("/api/admin/conversations"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.code").value(0))
           .andExpect(jsonPath("$.data[?(@.title=='审计会话')]").exists());
    }
}
```
- [ ] **Step 2: 运行确认失败**
- [ ] **Step 3:** `ConversationRepository` 加 `List<ConversationEntity> findAllByOrderByUpdatedAtDesc();`
- [ ] **Step 4:** `ChatController` 加：
```java
    @GetMapping("/api/admin/conversations")
    public ApiResponse<List<ConversationResponse>> allConversations() {
        return ApiResponse.ok(conversations.findAllByOrderByUpdatedAtDesc()
                .stream().map(ConversationResponse::from).toList());
    }

    @GetMapping("/api/admin/conversations/{id}/messages")
    public ApiResponse<List<MessageResponse>> adminHistory(@PathVariable Long id) {
        return history(id);
    }
```
- [ ] **Step 5: 运行确认通过；Step 6: 提交** `git commit -m "后端补 admin 会话审计端点"`

---

### Task 1: 前端脚手架 + 路由 + API 层 + SSE 工具（含 SSE 解析单测）

**Files:** `package.json`、`vite.config.js`、`index.html`、`vitest.config.js`、`src/main.js`、`src/App.vue`、`src/router/index.js`、`src/api/http.js`、`src/api/sse.js`、`src/api/index.js`、`src/stores/user.js`、`src/layouts/MainLayout.vue`；Test: `src/api/sse.test.js`

**Interfaces:**
- `parseSseChunk(buffer) -> { events: [{event, data}], rest }`：从累积字符串里切出完整 SSE 事件（以 `\n\n` 分隔，解析 `event:`/`data:` 行），返回已解析事件与剩余未完成片段。
- `streamChat(body, { onEvent, onError, onDone })`：`fetch('/api/chat', POST, json)`，读 `response.body` reader，增量喂给 `parseSseChunk`，对每个事件回调 `onEvent({event, data})`。
- `api`：`listAgents/listMyConversations/getMessages`、`adminAgents.*`、`adminKbs.*`、`adminTools.*`、`adminConversations.*`。

- [ ] **Step 1: package.json**
```json
{
  "name": "agent-platform-frontend",
  "private": true,
  "version": "0.0.1",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vite build",
    "preview": "vite preview",
    "test": "vitest run"
  },
  "dependencies": {
    "axios": "^1.7.9",
    "element-plus": "^2.9.1",
    "pinia": "^2.3.0",
    "vue": "^3.5.13",
    "vue-router": "^4.5.0"
  },
  "devDependencies": {
    "@vitejs/plugin-vue": "^5.2.1",
    "vite": "^6.0.5",
    "vitest": "^2.1.8"
  }
}
```
- [ ] **Step 2: vite.config.js**（含 `/api` 代理到 8080）
```js
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: true } }
  }
})
```
- [ ] **Step 3: vitest.config.js**
```js
import { defineConfig } from 'vitest/config'
export default defineConfig({ test: { environment: 'node' } })
```
- [ ] **Step 4: index.html**
```html
<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>Agent 平台</title>
  </head>
  <body>
    <div id="app"></div>
    <script type="module" src="/src/main.js"></script>
  </body>
</html>
```
- [ ] **Step 5: src/api/sse.js（先写，配合单测）**
```js
// Parse accumulated SSE text into complete events; return events + leftover.
export function parseSseChunk(buffer) {
  const events = []
  let rest = buffer
  let idx
  while ((idx = rest.indexOf('\n\n')) !== -1) {
    const block = rest.slice(0, idx)
    rest = rest.slice(idx + 2)
    let event = 'message'
    const dataLines = []
    for (const line of block.split('\n')) {
      if (line.startsWith('event:')) event = line.slice(6).trim()
      else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
    }
    if (dataLines.length) events.push({ event, data: dataLines.join('\n') })
  }
  return { events, rest }
}

export async function streamChat(body, { onEvent, onError, onDone }) {
  try {
    const resp = await fetch('/api/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
      body: JSON.stringify(body)
    })
    const reader = resp.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    for (;;) {
      const { value, done } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const { events, rest } = parseSseChunk(buffer)
      buffer = rest
      for (const e of events) onEvent && onEvent(e)
    }
    onDone && onDone()
  } catch (err) {
    onError && onError(err)
  }
}
```
- [ ] **Step 6: src/api/sse.test.js**
```js
import { describe, it, expect } from 'vitest'
import { parseSseChunk } from './sse.js'

describe('parseSseChunk', () => {
  it('parses complete events and keeps leftover', () => {
    const input = 'event:meta\ndata:{"conversationId":1}\n\nevent:token\ndata:Hello\n\nevent:tok'
    const { events, rest } = parseSseChunk(input)
    expect(events).toHaveLength(2)
    expect(events[0].event).toBe('meta')
    expect(events[1].data).toBe('Hello')
    expect(rest).toBe('event:tok')
  })
})
```
- [ ] **Step 7: src/api/http.js**
```js
import axios from 'axios'
import { ElMessage } from 'element-plus'

const http = axios.create({ baseURL: '/' })

http.interceptors.response.use(
  (resp) => {
    const body = resp.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code !== 0) {
        ElMessage.error(body.message || '请求失败')
        return Promise.reject(new Error(body.message))
      }
      return body.data
    }
    return body
  },
  (err) => {
    ElMessage.error(err.message || '网络错误')
    return Promise.reject(err)
  }
)

export default http
```
- [ ] **Step 8: src/api/index.js**
```js
import http from './http.js'

export const api = {
  listAgents: () => http.get('/api/agents'),
  listMyConversations: (userId) => http.get('/api/conversations', { params: { userId } }),
  getMessages: (id) => http.get(`/api/conversations/${id}/messages`),

  adminAgents: {
    list: () => http.get('/api/admin/agents'),
    create: (data) => http.post('/api/admin/agents', data),
    update: (id, data) => http.put(`/api/admin/agents/${id}`, data),
    remove: (id) => http.delete(`/api/admin/agents/${id}`),
    bindings: (id, data) => http.put(`/api/admin/agents/${id}/bindings`, data)
  },
  adminKbs: {
    list: () => http.get('/api/admin/knowledge-bases'),
    create: (data) => http.post('/api/admin/knowledge-bases', data),
    remove: (id) => http.delete(`/api/admin/knowledge-bases/${id}`),
    listDocs: (id) => http.get(`/api/admin/knowledge-bases/${id}/documents`),
    removeDoc: (id, docId) => http.delete(`/api/admin/knowledge-bases/${id}/documents/${docId}`),
    retrieve: (id, query, topK) => http.post(`/api/admin/knowledge-bases/${id}/retrieve`, { query, topK }),
    uploadUrl: (id) => `/api/admin/knowledge-bases/${id}/documents`
  },
  adminTools: {
    list: () => http.get('/api/admin/tools'),
    create: (data) => http.post('/api/admin/tools', data),
    update: (id, data) => http.put(`/api/admin/tools/${id}`, data),
    remove: (id) => http.delete(`/api/admin/tools/${id}`),
    test: (id, args) => http.post(`/api/admin/tools/${id}/test`, { args })
  },
  adminConversations: {
    list: () => http.get('/api/admin/conversations'),
    messages: (id) => http.get(`/api/admin/conversations/${id}/messages`)
  }
}
```
- [ ] **Step 9: src/stores/user.js**
```js
import { defineStore } from 'pinia'

export const useUserStore = defineStore('user', {
  state: () => ({ userId: localStorage.getItem('userId') || '' }),
  actions: {
    ensureUserId() {
      if (!this.userId) {
        this.userId = 'u-' + Math.floor(Date.now() % 1e9) + '-' + Math.floor(Math.random() * 1e4)
        localStorage.setItem('userId', this.userId)
      }
      return this.userId
    }
  }
})
```
- [ ] **Step 10: src/router/index.js**
```js
import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '../layouts/MainLayout.vue'

const routes = [
  {
    path: '/',
    component: MainLayout,
    children: [
      { path: '', redirect: '/chat' },
      { path: 'chat', component: () => import('../views/chat/ChatView.vue') },
      { path: 'admin/agents', component: () => import('../views/admin/AgentsView.vue') },
      { path: 'admin/knowledge', component: () => import('../views/admin/KnowledgeView.vue') },
      { path: 'admin/tools', component: () => import('../views/admin/ToolsView.vue') },
      { path: 'admin/conversations', component: () => import('../views/admin/ConversationsView.vue') }
    ]
  }
]

export default createRouter({ history: createWebHistory(), routes })
```
- [ ] **Step 11: src/main.js / src/App.vue / src/layouts/MainLayout.vue**
```js
// main.js
import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router/index.js'

createApp(App).use(createPinia()).use(router).use(ElementPlus).mount('#app')
```
```vue
<!-- App.vue -->
<template><router-view /></template>
```
```vue
<!-- layouts/MainLayout.vue -->
<script setup>
import { useRoute } from 'vue-router'
const route = useRoute()
</script>
<template>
  <el-container style="height: 100vh">
    <el-aside width="200px" style="background:#304156">
      <div style="color:#fff;padding:16px;font-weight:bold">Agent 平台</div>
      <el-menu :default-active="route.path" router background-color="#304156"
               text-color="#fff" active-text-color="#ffd04b">
        <el-menu-item index="/chat">对话</el-menu-item>
        <el-menu-item index="/admin/agents">Agent 管理</el-menu-item>
        <el-menu-item index="/admin/knowledge">知识库</el-menu-item>
        <el-menu-item index="/admin/tools">工具</el-menu-item>
        <el-menu-item index="/admin/conversations">会话审计</el-menu-item>
      </el-menu>
    </el-aside>
    <el-main style="padding:0"><router-view /></el-main>
  </el-container>
</template>
```
- [ ] **Step 12: 安装依赖 + 单测**
Run: `cd agent-platform/frontend && npm install && npm run test`
Expected: `parseSseChunk` 测试通过。
- [ ] **Step 13: 提交** `git commit -m "前端脚手架: 路由/API层/SSE工具与单测"`

---

### Task 2: 用户对话端 ChatView

**Files:** `src/views/chat/ChatView.vue`

**Interfaces:** 左侧 Agent 列表 + 我的会话；右侧消息流；`streamChat` 处理 `meta`(存 conversationId)/`step`(折叠展示)/`token`(累加)/`done`。

- [ ] **Step 1: ChatView.vue**
```vue
<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../../api/index.js'
import { streamChat } from '../../api/sse.js'
import { useUserStore } from '../../stores/user.js'

const userStore = useUserStore()
const userId = userStore.ensureUserId()
const agents = ref([])
const conversations = ref([])
const currentAgent = ref(null)
const conversationId = ref(null)
const messages = ref([])
const input = ref('')
const sending = ref(false)

async function loadAgents() { agents.value = await api.listAgents() }
async function loadConversations() { conversations.value = await api.listMyConversations(userId) }

function selectAgent(a) {
  currentAgent.value = a
  conversationId.value = null
  messages.value = []
}

async function openConversation(c) {
  conversationId.value = c.id
  currentAgent.value = agents.value.find((a) => a.id === c.agentId) || currentAgent.value
  const history = await api.getMessages(c.id)
  messages.value = history.map((m) => ({ role: m.role, content: m.content, steps: [] }))
}

async function send() {
  if (!currentAgent.value || !input.value.trim() || sending.value) return
  const text = input.value.trim()
  input.value = ''
  messages.value.push({ role: 'user', content: text, steps: [] })
  const assistant = { role: 'assistant', content: '', steps: [] }
  messages.value.push(assistant)
  sending.value = true
  await streamChat(
    { agentId: currentAgent.value.id, conversationId: conversationId.value, message: text, userId },
    {
      onEvent: (e) => {
        if (e.event === 'meta') {
          try { conversationId.value = JSON.parse(e.data).conversationId } catch (_) {}
        } else if (e.event === 'token') {
          try { assistant.content += JSON.parse(e.data).content || '' } catch (_) { assistant.content += e.data }
        } else if (e.event === 'step') {
          try { assistant.steps.push(JSON.parse(e.data).content || '') } catch (_) { assistant.steps.push(e.data) }
        }
      },
      onError: () => { assistant.content += '\n[出错了]' ; sending.value = false },
      onDone: () => { sending.value = false; loadConversations() }
    }
  )
}

onMounted(() => { loadAgents(); loadConversations() })
</script>

<template>
  <el-container style="height:100vh">
    <el-aside width="280px" style="border-right:1px solid #eee;padding:12px;overflow:auto">
      <h4>选择 Agent</h4>
      <el-card v-for="a in agents" :key="a.id" shadow="hover" style="margin-bottom:8px;cursor:pointer"
               :class="{ active: currentAgent && currentAgent.id === a.id }" @click="selectAgent(a)">
        <b>{{ a.name }}</b>
        <div style="font-size:12px;color:#999">{{ a.description || a.agentType }}</div>
      </el-card>
      <el-divider>我的会话</el-divider>
      <div v-for="c in conversations" :key="c.id" class="conv" @click="openConversation(c)">{{ c.title }}</div>
    </el-aside>
    <el-main style="display:flex;flex-direction:column">
      <div style="flex:1;overflow:auto;padding:12px">
        <div v-for="(m, i) in messages" :key="i" style="margin-bottom:14px">
          <el-tag :type="m.role === 'user' ? 'primary' : 'success'" size="small">{{ m.role }}</el-tag>
          <el-collapse v-if="m.steps && m.steps.length" style="margin:6px 0">
            <el-collapse-item title="执行过程">
              <div v-for="(s, si) in m.steps" :key="si" style="font-size:12px;color:#666">{{ s }}</div>
            </el-collapse-item>
          </el-collapse>
          <div style="white-space:pre-wrap;margin-top:4px">{{ m.content }}</div>
        </div>
      </div>
      <div style="padding:12px;border-top:1px solid #eee;display:flex;gap:8px">
        <el-input v-model="input" placeholder="输入消息，回车发送" @keyup.enter="send" :disabled="!currentAgent" />
        <el-button type="primary" :loading="sending" @click="send" :disabled="!currentAgent">发送</el-button>
      </div>
    </el-main>
  </el-container>
</template>

<style scoped>
.active { border:1px solid #409eff }
.conv { padding:6px 8px;cursor:pointer;border-radius:4px }
.conv:hover { background:#f5f7fa }
</style>
```
- [ ] **Step 2: 提交** `git commit -m "前端: 用户对话端(流式/步骤/会话历史)"`

---

### Task 3: 管理后台 4 个页面

**Files:** `src/views/admin/AgentsView.vue`、`KnowledgeView.vue`、`ToolsView.vue`、`ConversationsView.vue`

**Interfaces:** 表格 + 表单对话框，调用 `api.admin*`。

- [ ] **Step 1: AgentsView.vue**（表格 + 新建/编辑抽屉 + 绑定知识库/工具）
```vue
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
```
- [ ] **Step 2: KnowledgeView.vue**（知识库列表 + 文档上传/状态 + 检索测试）
```vue
<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../../api/index.js'
import { useUserStore } from '../../stores/user.js'
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
  <div style="padding:16px">
    <el-button type="primary" @click="create">新建知识库</el-button>
    <el-table :data="list" style="margin-top:12px">
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
```
- [ ] **Step 3: ToolsView.vue**
```vue
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
  <div style="padding:16px">
    <el-button type="primary" @click="create">新建工具</el-button>
    <el-table :data="list" style="margin-top:12px">
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
```
- [ ] **Step 4: ConversationsView.vue**
```vue
<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../../api/index.js'

const list = ref([]); const dialog = ref(false); const msgs = ref([])
async function load() { list.value = await api.adminConversations.list() }
async function view(row) { msgs.value = await api.adminConversations.messages(row.id); dialog.value = true }
onMounted(load)
</script>

<template>
  <div style="padding:16px">
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
```
- [ ] **Step 5: 构建验证** `cd agent-platform/frontend && npm run build`，Expected: 构建成功。
- [ ] **Step 6: 提交** `git commit -m "前端: 管理后台(Agent/知识库/工具/会话审计)"`

---

### Task 4: README 与启动说明

**Files:** `agent-platform/README.md`

- [ ] **Step 1:** 写 README：docker compose 起 MySQL+Redis；后端 `./mvnw spring-boot:run`（带 `.env`/环境变量）；前端 `npm install && npm run dev`（Vite 代理到 8080）；浏览器 `http://localhost:5173`。
- [ ] **Step 2: 提交** `git commit -m "添加 agent-platform README 启动说明"`

---

## Self-Review
- 用户对话端（选 Agent/流式/步骤/引用经由 step+token）✅；管理后台 4 页 ✅；admin 会话审计端点后端补齐 ✅。
- `.gitignore` 需忽略 `frontend/node_modules` 与 `frontend/dist`。
- 验证：SSE 解析单测 + `npm run build` 构建通过；后端 admin 端点集成测试。
- 风险：Element Plus / Vite 具体小版本以 `npm install` 解析为准；前端仅做构建级验证，交互以本地联调为准。
