<script setup>
import { ref, onMounted, nextTick, computed } from 'vue'
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
const scroller = ref(null)

const typeLabel = { chat: '对话', rag: '知识', tool: '工具', react: '多步骤' }
function initial(name) { return (name || '?').trim().charAt(0) }

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
  scrollToBottom()
}

function scrollToBottom() {
  nextTick(() => {
    if (scroller.value) scroller.value.scrollTop = scroller.value.scrollHeight
  })
}

async function send() {
  if (!currentAgent.value || !input.value.trim() || sending.value) return
  const text = input.value.trim()
  input.value = ''
  messages.value.push({ role: 'user', content: text, steps: [] })
  const assistant = { role: 'assistant', content: '', steps: [], sources: [], error: '' }
  messages.value.push(assistant)
  sending.value = true
  scrollToBottom()
  const pick = (data) => {
    try { return JSON.parse(data).content || '' } catch (_) { return data }
  }
  await streamChat(
    { agentId: currentAgent.value.id, conversationId: conversationId.value, message: text, userId },
    {
      onEvent: (e) => {
        if (e.event === 'meta') {
          try { conversationId.value = JSON.parse(e.data).conversationId } catch (_) {}
        } else if (e.event === 'token') {
          assistant.content += pick(e.data)
        } else if (e.event === 'step') {
          assistant.steps.push(pick(e.data))
        } else if (e.event === 'source') {
          assistant.sources.push(pick(e.data))
        } else if (e.event === 'error') {
          assistant.error = pick(e.data)
        }
        scrollToBottom()
      },
      onError: (err) => { assistant.error = err.message || '连接出错，请重试'; sending.value = false },
      onDone: () => { sending.value = false; loadConversations(); scrollToBottom() }
    }
  )
}

const empty = computed(() => messages.value.length === 0)

onMounted(() => { loadAgents(); loadConversations() })
</script>

<template>
  <div class="chat">
    <!-- left rail: agents + history -->
    <div class="side">
      <div class="side-title">选择 Agent</div>
      <div class="agent-list">
        <button
          v-for="a in agents"
          :key="a.id"
          class="agent"
          :class="{ on: currentAgent && currentAgent.id === a.id }"
          @click="selectAgent(a)"
        >
          <span class="ava">{{ initial(a.name) }}</span>
          <span class="meta">
            <span class="nm">{{ a.name }}</span>
            <span class="sub">{{ a.description || (typeLabel[a.agentType] + ' Agent') }}</span>
          </span>
        </button>
        <div v-if="!agents.length" class="hint">还没有启用的 Agent</div>
      </div>

      <div class="side-title gap">我的会话</div>
      <div class="conv-list">
        <button v-for="c in conversations" :key="c.id" class="conv" @click="openConversation(c)">
          {{ c.title || '未命名会话' }}
        </button>
        <div v-if="!conversations.length" class="hint">暂无历史</div>
      </div>
    </div>

    <!-- main: conversation -->
    <div class="main">
      <header class="topbar" v-if="currentAgent">
        <span class="ava sm">{{ initial(currentAgent.name) }}</span>
        <div class="tb-meta">
          <div class="tb-name">{{ currentAgent.name }}</div>
          <div class="tb-sub">{{ currentAgent.model }} · {{ typeLabel[currentAgent.agentType] }}</div>
        </div>
      </header>
      <header class="topbar" v-else>
        <div class="tb-meta"><div class="tb-name">对话</div></div>
      </header>

      <div class="stream" ref="scroller">
        <div v-if="empty" class="welcome">
          <div class="welcome-mark brand-serif">A</div>
          <h3>{{ currentAgent ? `开始与「${currentAgent.name}」对话` : '选择一个 Agent 开始' }}</h3>
          <p>在左侧挑选 Agent，问它任何问题。</p>
        </div>

        <div v-for="(m, i) in messages" :key="i" class="row" :class="m.role">
          <div class="bubble">
            <div v-if="m.steps && m.steps.length" class="steps">
              <el-collapse>
                <el-collapse-item :title="`执行过程 · ${m.steps.length} 步`">
                  <div v-for="(s, si) in m.steps" :key="si" class="step">{{ s }}</div>
                </el-collapse-item>
              </el-collapse>
            </div>
            <div class="text">{{ m.content || (m.role === 'assistant' && sending && !m.error ? '思考中…' : '') }}</div>
            <div v-if="m.error" class="err">{{ m.error }}</div>
            <div v-if="m.sources && m.sources.length" class="sources">
              <div class="sources-title">引用来源 · {{ m.sources.length }}</div>
              <div v-for="(s, si) in m.sources" :key="si" class="source">{{ s }}</div>
            </div>
          </div>
        </div>
      </div>

      <div class="composer">
        <div class="composer-inner">
          <textarea
            v-model="input"
            class="composer-input"
            :placeholder="currentAgent ? '输入消息，回车发送，Shift+回车换行' : '请先选择一个 Agent'"
            :disabled="!currentAgent || sending"
            rows="1"
            @keydown.enter.exact.prevent="send"
          />
          <button class="send" :disabled="!currentAgent || sending || !input.trim()" @click="send">
            {{ sending ? '…' : '发送' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chat { display: flex; height: 100vh; }

/* ---- left rail ---- */
.side {
  width: 280px;
  flex-shrink: 0;
  border-right: 1px solid var(--line);
  background: var(--surface-2);
  padding: 20px 14px;
  display: flex;
  flex-direction: column;
  overflow: auto;
}
.side-title {
  font-size: 12px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--muted);
  padding: 0 8px 10px;
}
.side-title.gap { margin-top: 20px; }
.agent-list { display: flex; flex-direction: column; gap: 6px; }
.agent {
  display: flex;
  align-items: center;
  gap: 11px;
  text-align: left;
  padding: 10px;
  border: 1px solid transparent;
  border-radius: 12px;
  background: transparent;
  cursor: pointer;
  transition: all 0.16s ease;
  font: inherit;
}
.agent:hover { background: var(--surface); border-color: var(--line); }
.agent.on {
  background: var(--surface);
  border-color: var(--clay);
  box-shadow: 0 2px 10px rgba(194, 105, 63, 0.12);
}
.ava {
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  border-radius: 10px;
  background: var(--clay-tint);
  color: var(--clay-deep);
  display: grid;
  place-items: center;
  font-weight: 700;
  font-size: 16px;
}
.ava.sm { width: 32px; height: 32px; border-radius: 9px; font-size: 14px; }
.agent .meta { display: flex; flex-direction: column; min-width: 0; }
.agent .nm { font-weight: 600; color: var(--ink); font-size: 14.5px; }
.agent .sub {
  font-size: 12px; color: var(--muted);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 190px;
}
.conv-list { display: flex; flex-direction: column; gap: 2px; }
.conv {
  text-align: left; font: inherit; cursor: pointer;
  padding: 9px 10px; border-radius: 9px; border: none; background: transparent;
  color: var(--ink-soft); font-size: 13.5px;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.conv:hover { background: var(--surface); color: var(--ink); }
.hint { padding: 8px 10px; font-size: 13px; color: var(--muted); }

/* ---- main ---- */
.main { flex: 1; min-width: 0; display: flex; flex-direction: column; background: var(--canvas); }
.topbar {
  display: flex; align-items: center; gap: 11px;
  padding: 14px 28px; border-bottom: 1px solid var(--line);
  background: rgba(255, 253, 249, 0.7);
  backdrop-filter: blur(6px);
}
.tb-meta { display: flex; flex-direction: column; }
.tb-name { font-weight: 650; color: var(--ink); font-size: 15.5px; }
.tb-sub { font-size: 12px; color: var(--muted); }

.stream { flex: 1; overflow: auto; padding: 28px 0; }
.welcome { text-align: center; margin: 12vh auto 0; color: var(--ink-soft); }
.welcome-mark {
  width: 64px; height: 64px; margin: 0 auto 18px;
  border-radius: 18px; background: var(--clay); color: #fff;
  display: grid; place-items: center; font-size: 32px; font-weight: 700;
  box-shadow: 0 8px 24px rgba(194, 105, 63, 0.3);
}
.welcome h3 { margin: 0 0 6px; font-size: 19px; font-weight: 650; color: var(--ink); }
.welcome p { margin: 0; font-size: 14px; color: var(--muted); }

.row { display: flex; padding: 0 28px; margin-bottom: 18px; }
.row.user { justify-content: flex-end; }
.bubble { max-width: 720px; }
.row.user .bubble {
  background: var(--clay-tint);
  border: 1px solid #efddd0;
  border-radius: 16px 16px 4px 16px;
  padding: 12px 16px;
}
.row.assistant .bubble {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 16px 16px 16px 4px;
  padding: 14px 18px;
  box-shadow: 0 1px 2px rgba(60, 50, 38, 0.04);
}
.text { white-space: pre-wrap; line-height: 1.7; color: var(--ink); font-size: 14.5px; }
.steps { margin-bottom: 8px; }
.steps :deep(.el-collapse) { border: none; --el-collapse-border-color: var(--line); }
.steps :deep(.el-collapse-item__header) {
  height: 32px; font-size: 12.5px; color: var(--clay-deep);
  background: transparent; border: none; font-weight: 600;
}
.steps :deep(.el-collapse-item__wrap) { background: transparent; border: none; }
.step {
  font-size: 12.5px; color: var(--ink-soft); line-height: 1.6;
  padding: 8px 10px; margin: 4px 0; border-left: 2px solid var(--clay);
  background: var(--surface-2); border-radius: 0 8px 8px 0;
  white-space: pre-wrap; word-break: break-word;
}
.err {
  margin-top: 8px; font-size: 13px; color: #a8442f;
  background: #fbeae5; border: 1px solid #f0cabb;
  padding: 8px 12px; border-radius: 8px;
}
.sources {
  margin-top: 12px; padding-top: 10px; border-top: 1px dashed var(--line);
}
.sources-title {
  font-size: 11.5px; letter-spacing: 0.06em; color: var(--muted);
  text-transform: uppercase; margin-bottom: 6px;
}
.source {
  font-size: 12.5px; color: var(--ink-soft); line-height: 1.55;
  padding: 6px 10px; margin: 4px 0;
  background: var(--sage-tint); border-radius: 8px;
  border-left: 2px solid var(--sage);
  white-space: pre-wrap; word-break: break-word;
}

/* ---- composer ---- */
.composer { padding: 14px 28px 22px; }
.composer-inner {
  display: flex; align-items: flex-end; gap: 10px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 18px;
  padding: 8px 8px 8px 16px;
  box-shadow: 0 4px 18px rgba(60, 50, 38, 0.05);
  transition: border-color 0.16s ease, box-shadow 0.16s ease;
  max-width: 920px; margin: 0 auto;
}
.composer-inner:focus-within {
  border-color: var(--clay);
  box-shadow: 0 4px 22px rgba(194, 105, 63, 0.14);
}
.composer-input {
  flex: 1; border: none; outline: none; resize: none;
  background: transparent; font: inherit; font-size: 14.5px;
  line-height: 1.6; color: var(--ink); max-height: 160px; padding: 6px 0;
}
.composer-input::placeholder { color: var(--muted); }
.send {
  flex-shrink: 0; border: none; cursor: pointer;
  background: var(--clay); color: #fff;
  border-radius: 12px; padding: 0 18px; height: 38px;
  font-weight: 600; font-size: 14px;
  transition: background 0.16s ease;
}
.send:hover:not(:disabled) { background: var(--clay-deep); }
.send:disabled { background: var(--sand-deep); color: #fff; cursor: not-allowed; }
</style>
