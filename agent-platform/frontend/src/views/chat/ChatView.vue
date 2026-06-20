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
      onError: () => { assistant.content += '\n[出错了]'; sending.value = false },
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
.active { border: 1px solid #409eff }
.conv { padding: 6px 8px; cursor: pointer; border-radius: 4px }
.conv:hover { background: #f5f7fa }
</style>
