<script setup>
import { ref, onMounted, nextTick, computed } from 'vue'
import { api } from '../../api/index.js'
import { streamChat } from '../../api/sse.js'
import { useUserStore } from '../../stores/user.js'
import { renderMarkdown } from '../../utils/markdown.js'
import ToolTrace from '../../components/ToolTrace.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { SendHorizontal, Square, Search, Plus, MoreHorizontal, Pencil, Trash2, ChevronDown, Check } from 'lucide-vue-next'

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
const composer = ref(null)
const agentMenu = ref(false)
const agentQuery = ref('')

const typeLabel = { chat: '对话', rag: '知识', tool: '工具', react: '多步骤' }
const examples = ['用一句话介绍你的能力', '帮我把这段话润色得更正式一点', '用工具计算 128 + 256', '给我三个本周可以做的小事']

// capability chips for the picker / gallery — only show non-zero bindings
function caps(a) {
  const out = []
  if (a.toolCount) out.push(`工具 ${a.toolCount}`)
  if (a.kbCount) out.push(`知识库 ${a.kbCount}`)
  if (a.mcpCount) out.push(`MCP ${a.mcpCount}`)
  return out
}
// persisted ReAct trajectory -> step objects, so reopened conversations replay the tool trace
function parseSteps(toolCalls) {
  if (!toolCalls) return []
  try { const a = JSON.parse(toolCalls); return Array.isArray(a) ? a : [] } catch (_) { return [] }
}

let abort = null

function initial(name) { return (name || '?').trim().charAt(0) }
function agentName(id) { const a = agents.value.find((x) => x.id === id); return a ? a.name : '会话' }
function relTime(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (isNaN(d.getTime())) return ''
  const pad = (n) => String(n).padStart(2, '0')
  const today = new Date().toDateString() === d.toDateString()
  return today ? `今天 ${pad(d.getHours())}:${pad(d.getMinutes())}` : `${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}
const empty = computed(() => messages.value.length === 0)
const filteredAgents = computed(() => {
  const q = agentQuery.value.trim().toLowerCase()
  if (!q) return agents.value
  return agents.value.filter((a) =>
    (a.name || '').toLowerCase().includes(q) || (a.description || '').toLowerCase().includes(q))
})

async function loadAgents() { agents.value = await api.listAgents() }
async function loadConversations() { conversations.value = await api.listMyConversations(userId) }

function selectAgent(a) {
  currentAgent.value = a
  conversationId.value = null
  messages.value = []
  agentMenu.value = false
  agentQuery.value = ''
  nextTick(() => composer.value && composer.value.focus())
}
function newChat() {
  conversationId.value = null
  messages.value = []
  nextTick(() => composer.value && composer.value.focus())
}
async function openConversation(c) {
  conversationId.value = c.id
  currentAgent.value = agents.value.find((a) => a.id === c.agentId) || currentAgent.value
  const history = await api.getMessages(c.id)
  messages.value = history.map((m) => ({ role: m.role, content: m.content, steps: parseSteps(m.toolCalls), sources: [], error: '' }))
  scrollToBottom()
}
async function renameConversation(c) {
  try {
    const { value } = await ElMessageBox.prompt('重命名会话', '重命名', {
      inputValue: c.title || '', confirmButtonText: '保存', cancelButtonText: '取消'
    })
    await api.renameConversation(c.id, value)
    await loadConversations()
  } catch (_) {}
}
async function removeConversation(c) {
  try {
    await ElMessageBox.confirm(`删除会话「${c.title || '未命名会话'}」?此操作不可恢复。`, '删除会话', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'
    })
    await api.deleteConversation(c.id)
    if (conversationId.value === c.id) newChat()
    await loadConversations()
    ElMessage.success('已删除')
  } catch (_) {}
}

function scrollToBottom() {
  nextTick(() => { if (scroller.value) scroller.value.scrollTop = scroller.value.scrollHeight })
}

const pick = (data) => { try { return JSON.parse(data).content || '' } catch (_) { return data } }
// step content is itself JSON ({tool,args,result,ok}); fall back to legacy string
const pickStep = (data) => { const c = pick(data); try { return JSON.parse(c) } catch (_) { return c } }

function useExample(t) { input.value = t; nextTick(() => composer.value && composer.value.focus()) }

async function send() {
  if (!currentAgent.value || !input.value.trim() || sending.value) return
  const text = input.value.trim()
  input.value = ''
  messages.value.push({ role: 'user', content: text, steps: [] })
  messages.value.push({ role: 'assistant', content: '', steps: [], sources: [], error: '' })
  const assistant = messages.value[messages.value.length - 1]
  sending.value = true
  scrollToBottom()
  abort = new AbortController()
  await streamChat(
    { agentId: currentAgent.value.id, conversationId: conversationId.value, message: text, userId },
    {
      signal: abort.signal,
      onEvent: (e) => {
        if (e.event === 'meta') { try { conversationId.value = JSON.parse(e.data).conversationId } catch (_) {} }
        else if (e.event === 'token') assistant.content += pick(e.data)
        else if (e.event === 'step') assistant.steps.push(pickStep(e.data))
        else if (e.event === 'source') assistant.sources.push(pick(e.data))
        else if (e.event === 'error') assistant.error = pick(e.data)
        scrollToBottom()
      },
      onError: (err) => { assistant.error = err.message || '连接出错，请重试'; sending.value = false },
      onDone: () => { sending.value = false; abort = null; loadConversations(); scrollToBottom() }
    }
  )
}
function stop() { if (abort) { abort.abort(); abort = null } sending.value = false }

onMounted(() => { loadAgents(); loadConversations() })
</script>

<template>
  <div class="chat">
    <aside class="history">
      <button class="new-btn" @click="newChat"><Plus :size="16" :stroke-width="2" /> 新对话</button>
      <div class="h-title">我的会话</div>
      <div class="conv-list">
        <div
          v-for="c in conversations" :key="c.id"
          class="conv" :class="{ on: conversationId === c.id }"
          @click="openConversation(c)"
        >
          <div class="conv-main">
            <span class="conv-t">{{ c.title || '未命名会话' }}</span>
            <span class="conv-sub">{{ agentName(c.agentId) }} · {{ relTime(c.updatedAt) }}</span>
          </div>
          <el-dropdown
            trigger="click" placement="bottom-end"
            @command="(cmd) => cmd === 'rename' ? renameConversation(c) : removeConversation(c)"
          >
            <span class="conv-more" @click.stop><MoreHorizontal :size="16" /></span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="rename"><Pencil :size="14" :stroke-width="2" style="margin-right:7px" />重命名</el-dropdown-item>
                <el-dropdown-item command="remove"><Trash2 :size="14" :stroke-width="2" style="margin-right:7px" />删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
        <div v-if="!conversations.length" class="hint">暂无历史</div>
      </div>
    </aside>

    <section class="main">
      <header class="topbar">
        <el-popover
          v-model:visible="agentMenu" trigger="click" :width="300"
          placement="bottom-start" popper-class="agent-pop" :teleported="true"
        >
          <template #reference>
            <button class="switcher">
              <template v-if="currentAgent">
                <span class="ava sm">{{ initial(currentAgent.name) }}</span>
                <span class="sw-name">{{ currentAgent.name }}</span>
                <span class="sw-type">{{ typeLabel[currentAgent.agentType] }}</span>
              </template>
              <span v-else class="sw-ph">选择 Agent</span>
              <ChevronDown class="sw-chev" :size="15" />
            </button>
          </template>
          <div class="ap">
            <el-input v-model="agentQuery" placeholder="搜索 Agent…" size="small">
              <template #prefix><Search :size="15" :stroke-width="2" /></template>
            </el-input>
            <div class="ap-list">
              <button
                v-for="a in filteredAgents" :key="a.id"
                class="ap-item" :class="{ on: currentAgent && currentAgent.id === a.id }"
                @click="selectAgent(a)"
              >
                <span class="ava sm">{{ initial(a.name) }}</span>
                <span class="ap-meta">
                  <span class="ap-name">{{ a.name }} <i class="ap-badge">{{ typeLabel[a.agentType] }}</i></span>
                  <span v-if="caps(a).length" class="ap-caps">
                    <i v-for="c in caps(a)" :key="c" class="cap">{{ c }}</i>
                  </span>
                  <span v-else class="ap-desc">{{ a.description || '—' }}</span>
                </span>
                <Check v-if="currentAgent && currentAgent.id === a.id" class="ap-check" :size="16" :stroke-width="2.4" />
              </button>
              <div v-if="!filteredAgents.length" class="hint">没有匹配的 Agent</div>
            </div>
          </div>
        </el-popover>
        <span v-if="currentAgent" class="tb-model">{{ currentAgent.model }}</span>
      </header>

      <div class="stream" ref="scroller">
        <div v-if="empty" class="welcome">
          <div class="welcome-mark brand-serif">A</div>
          <h3>{{ currentAgent ? `开始与「${currentAgent.name}」对话` : '挑一个 Agent 开始' }}</h3>
          <p>{{ currentAgent ? '问它任何问题，或试试下面的示例。' : '点击卡片选择一个 Agent。' }}</p>

          <div v-if="!currentAgent" class="gallery">
            <button v-for="a in agents" :key="a.id" class="gcard" @click="selectAgent(a)">
              <span class="ava">{{ initial(a.name) }}</span>
              <span class="g-head">
                <span class="g-name">{{ a.name }}</span>
                <span class="g-type">{{ typeLabel[a.agentType] }}</span>
              </span>
              <span class="g-desc">{{ a.description || '—' }}</span>
              <span v-if="caps(a).length" class="g-caps">
                <i v-for="c in caps(a)" :key="c" class="cap">{{ c }}</i>
              </span>
            </button>
            <div v-if="!agents.length" class="hint">还没有启用的 Agent</div>
          </div>
          <div v-else class="examples">
            <button v-for="ex in examples" :key="ex" class="ex" @click="useExample(ex)">{{ ex }}</button>
          </div>
        </div>

        <div v-else class="thread">
          <div v-for="(m, i) in messages" :key="i" class="row" :class="m.role">
            <template v-if="m.role === 'assistant'">
              <span class="ava sm a-ava">{{ initial(currentAgent && currentAgent.name) }}</span>
              <div class="bubble a-bubble">
                <ToolTrace v-if="m.steps && m.steps.length" :steps="m.steps" class="mb" />
                <div class="text md" v-html="renderMarkdown(m.content || (sending && !m.error ? '思考中…' : ''))"></div>
                <div v-if="m.error" class="err">{{ m.error }}</div>
                <div v-if="m.sources && m.sources.length" class="sources">
                  <div class="sources-title">引用来源 · {{ m.sources.length }}</div>
                  <div v-for="(s, si) in m.sources" :key="si" class="source">{{ s }}</div>
                </div>
              </div>
            </template>
            <div v-else class="bubble u-bubble">{{ m.content }}</div>
          </div>
        </div>
      </div>

      <div class="composer">
        <div class="composer-inner">
          <textarea
            ref="composer" v-model="input" class="composer-input"
            :placeholder="currentAgent ? '输入消息，回车发送，Shift+回车换行' : '请先选择一个 Agent'"
            :disabled="!currentAgent || sending" rows="1"
            @keydown.enter.exact.prevent="send"
          />
          <button v-if="sending" class="send stop" @click="stop"><Square :size="14" :stroke-width="2.2" /> 停止</button>
          <button v-else class="send" :disabled="!currentAgent || !input.trim()" @click="send">
            <SendHorizontal :size="18" :stroke-width="2" />
          </button>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.chat { display: flex; height: 100vh; }

/* ---- history rail ---- */
.history {
  width: 232px; flex-shrink: 0;
  border-right: 1px solid var(--line); background: var(--surface-2);
  padding: 16px 12px; display: flex; flex-direction: column;
}
.new-btn {
  display: flex; align-items: center; justify-content: center; gap: 7px;
  height: 38px; border: 1px solid var(--line); border-radius: 10px;
  background: var(--surface); color: var(--ink); font: inherit; font-weight: 600;
  cursor: pointer; transition: border-color 0.14s ease;
}
.new-btn:hover { border-color: var(--clay); }
.h-title {
  font-size: 11.5px; letter-spacing: 0.1em; text-transform: uppercase;
  color: var(--muted); padding: 18px 8px 8px;
}
.conv-list { display: flex; flex-direction: column; gap: 1px; overflow: auto; }
.conv {
  position: relative; display: flex; align-items: center; gap: 6px;
  padding: 8px 8px 8px 11px; border-radius: 10px; cursor: pointer;
}
.conv:hover { background: var(--surface); }
.conv.on { background: var(--clay-tint); }
.conv.on::before {
  content: ''; position: absolute; left: 3px; top: 9px; bottom: 9px;
  width: 3px; border-radius: 3px; background: var(--clay);
}
.conv-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 1px; }
.conv-t {
  font-size: 13.5px; color: var(--ink);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.conv.on .conv-t { font-weight: 600; color: var(--clay-deep); }
.conv-sub {
  font-size: var(--fs-2xs); color: var(--muted);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.conv.on .conv-sub { color: var(--clay); }
.conv-more { display: flex; opacity: 0; color: var(--muted); padding: 2px; border-radius: 5px; }
.conv:hover .conv-more { opacity: 1; }
.conv-more:hover { background: var(--surface-2); color: var(--ink); }
.hint { padding: 10px; font-size: 13px; color: var(--muted); }

/* ---- main ---- */
.main { flex: 1; min-width: 0; display: flex; flex-direction: column; background: var(--canvas); }
.topbar {
  display: flex; align-items: center; gap: 12px;
  padding: 12px 20px; border-bottom: 1px solid var(--line);
}
.switcher {
  display: inline-flex; align-items: center; gap: 8px;
  padding: 6px 10px; border: 1px solid var(--line); border-radius: 10px;
  background: var(--surface); cursor: pointer; font: inherit;
  transition: border-color 0.14s ease;
}
.switcher:hover { border-color: var(--clay); }
.sw-name { font-weight: 600; color: var(--ink); font-size: 14px; }
.sw-type, .sw-ph { font-size: 12px; color: var(--muted); }
.sw-ph { font-weight: 500; }
.sw-chev { font-size: 13px; color: var(--muted); }
.tb-model { font-size: 12px; color: var(--muted); }

.ava {
  width: 34px; height: 34px; flex-shrink: 0; border-radius: 9px;
  background: var(--clay-tint); color: var(--clay-deep);
  display: grid; place-items: center; font-weight: 700; font-size: 15px;
}
.ava.sm { width: 24px; height: 24px; border-radius: 7px; font-size: 12px; }

/* ---- stream ---- */
.stream { flex: 1; overflow: auto; }
.welcome { max-width: 720px; margin: 0 auto; text-align: center; padding: 10vh 24px 40px; }
.welcome-mark {
  width: 62px; height: 62px; margin: 0 auto 18px; border-radius: 19px;
  background: var(--clay); color: #fff; display: grid; place-items: center;
  font-size: var(--fs-display); font-weight: 600;
  box-shadow: 0 10px 28px -6px rgba(76, 102, 224, 0.42);
}
.welcome h3 { margin: 0 0 7px; font-size: 20px; font-weight: 600; letter-spacing: -0.01em; color: var(--ink); }
.welcome p { margin: 0 0 24px; font-size: var(--fs-body); color: var(--muted); }

.gallery {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 12px; text-align: left;
}
.gcard {
  display: flex; flex-direction: column; gap: 7px; align-items: flex-start;
  padding: 17px 18px; border: 1px solid var(--line); border-radius: var(--r-lg);
  background: var(--surface); cursor: pointer; font: inherit;
  box-shadow: var(--shadow-sm);
  transition: border-color 0.16s ease, transform 0.16s ease, box-shadow 0.2s ease;
}
.gcard:hover { border-color: #c6cef5; transform: translateY(-2px); box-shadow: var(--shadow-md); }
.g-head { display: flex; align-items: center; gap: 8px; }
.g-name { font-weight: 650; color: var(--ink); font-size: 14.5px; }
.g-type {
  font-size: 11px; color: var(--clay-deep); background: var(--clay-tint);
  padding: 1px 7px; border-radius: 6px;
}
.g-desc {
  font-size: 12.5px; color: var(--muted); line-height: 1.5;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
}
.g-caps { display: flex; flex-wrap: wrap; gap: 5px; margin-top: 2px; }
.cap {
  font-style: normal; font-size: 11px; color: var(--clay-deep);
  background: var(--clay-tint); padding: 1px 7px; border-radius: 6px;
}

.examples { display: flex; flex-wrap: wrap; gap: 8px; justify-content: center; }
.ex {
  padding: 8px 14px; border: 1px solid var(--line); border-radius: 20px;
  background: var(--surface); color: var(--ink-soft); font: inherit; font-size: 13px;
  cursor: pointer; transition: all 0.14s ease;
}
.ex:hover { border-color: var(--clay); color: var(--ink); }

/* ---- thread ---- */
.thread { max-width: 760px; margin: 0 auto; padding: 28px 24px 8px; }
.row { display: flex; gap: 12px; margin-bottom: 22px; }
.row.user { justify-content: flex-end; }
.a-ava { margin-top: 2px; }
.bubble { max-width: 92%; }
.u-bubble {
  background: var(--clay-tint); border: 1px solid #dde2fb;
  border-radius: 16px 16px 5px 16px; padding: 11px 16px;
  font-size: var(--fs-read); line-height: 1.65; color: #2f3c9e; max-width: 80%;
  box-shadow: 0 1px 2px rgba(58, 79, 191, 0.07);
  white-space: pre-wrap; word-break: break-word;
}
/* assistant content reads as a clean, full-width document — no bubble card */
.a-bubble { min-width: 0; max-width: 100%; padding-top: 1px; }
.mb { margin-bottom: 14px; }
.text { line-height: var(--lh-read); color: var(--ink); font-size: var(--fs-read); }

/* ---- refined markdown rendering ---- */
.text.md :deep(> :first-child) { margin-top: 0; }
.text.md :deep(> :last-child) { margin-bottom: 0; }
.text.md :deep(p) { margin: 0 0 11px; }
.text.md :deep(strong) { font-weight: 600; color: var(--ink); }
.text.md :deep(em) { font-style: italic; }
.text.md :deep(h1) { font-size: 19px; font-weight: 600; margin: 22px 0 10px; letter-spacing: -0.01em; }
.text.md :deep(h2) { font-size: 16.5px; font-weight: 600; margin: 20px 0 9px; letter-spacing: -0.008em; }
.text.md :deep(h3) { font-size: 15px; font-weight: 600; margin: 16px 0 7px; }
.text.md :deep(ul), .text.md :deep(ol) { margin: 8px 0 12px; padding-left: 24px; }
.text.md :deep(li) { margin: 5px 0; line-height: 1.72; }
.text.md :deep(li::marker) { color: var(--clay); font-weight: 600; }
.text.md :deep(li > ul), .text.md :deep(li > ol) { margin: 5px 0 3px; }
.text.md :deep(code) {
  background: var(--surface-2); border: 1px solid var(--line);
  border-radius: 5px; padding: 1.5px 6px; font-size: 0.86em;
  font-family: var(--font-mono); color: #b0432e;
}
.text.md :deep(pre) {
  background: #2c2a28; border-radius: var(--r-md); padding: 13px 15px;
  overflow-x: auto; margin: 10px 0; line-height: 1.6;
}
.text.md :deep(pre code) { background: none; border: none; padding: 0; color: #f0ede6; font-size: 13px; }
.text.md :deep(a) { color: var(--clay-deep); text-decoration: none; border-bottom: 1px solid currentColor; }
.text.md :deep(a:hover) { color: var(--clay); }
.text.md :deep(blockquote) {
  margin: 10px 0; padding: 2px 0 2px 14px;
  border-left: 3px solid var(--sand-deep); color: var(--ink-soft);
}
.text.md :deep(hr) { border: none; border-top: 1px solid var(--line); margin: 16px 0; }
.text.md :deep(table) { border-collapse: collapse; margin: 10px 0; font-size: 13.5px; }
.text.md :deep(th), .text.md :deep(td) { border: 1px solid var(--line); padding: 6px 11px; text-align: left; }
.text.md :deep(th) { background: var(--surface-2); font-weight: 600; }
.err {
  margin-top: 8px; font-size: 13px; color: #a8442f;
  background: #fbeae5; border: 1px solid #f0cabb; padding: 8px 12px; border-radius: 8px;
}
.sources { margin-top: 12px; padding-top: 10px; border-top: 1px dashed var(--line); }
.sources-title {
  font-size: 11.5px; letter-spacing: 0.06em; color: var(--muted);
  text-transform: uppercase; margin-bottom: 6px;
}
.source { font-size: 12.5px; color: var(--ink-soft); line-height: 1.5; margin-bottom: 4px; }

/* ---- composer ---- */
.composer { padding: 8px 24px 20px; }
.composer-inner {
  max-width: 760px; margin: 0 auto; display: flex; align-items: flex-end; gap: 10px;
  background: var(--surface); border: 1px solid var(--line); border-radius: var(--r-xl);
  padding: 9px 9px 9px 18px; box-shadow: var(--shadow-md);
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
}
.composer-inner:focus-within { border-color: var(--clay); box-shadow: var(--shadow-md), 0 0 0 3px rgba(76, 102, 224, 0.12); }
.composer-input {
  flex: 1; border: none; background: transparent; resize: none; outline: none;
  font: inherit; font-size: 14.5px; line-height: 1.6; color: var(--ink);
  max-height: 160px; padding: 6px 0;
}
.composer-input::placeholder { color: var(--muted); }
.send {
  flex-shrink: 0; display: inline-flex; align-items: center; gap: 5px;
  height: 38px; min-width: 38px; padding: 0 12px; border: none; border-radius: 11px;
  background: var(--clay); color: #fff; cursor: pointer; font: inherit; font-weight: 600;
  transition: opacity 0.14s ease, background 0.14s ease;
}
.send:disabled { opacity: 0.4; cursor: not-allowed; }
.send.stop { background: var(--surface-2); color: var(--ink); border: 1px solid var(--line); }

@media (max-width: 760px) {
  .history { display: none; }
  .thread, .composer-inner, .welcome { padding-left: 14px; padding-right: 14px; }
}
</style>

<style>
/* el-popover content is teleported to body — these can't be scoped */
.agent-pop { padding: 8px !important; }
.agent-pop .ap-list { margin-top: 8px; max-height: 320px; overflow: auto; display: flex; flex-direction: column; gap: 2px; }
.agent-pop .ap-item {
  display: flex; align-items: center; gap: 10px; width: 100%;
  padding: 8px; border: none; border-radius: 9px; background: transparent;
  cursor: pointer; font: inherit; text-align: left;
}
.agent-pop .ap-item:hover { background: var(--surface-2); }
.agent-pop .ap-item.on { background: var(--clay-tint); }
.agent-pop .ava {
  width: 30px; height: 30px; flex-shrink: 0; border-radius: 8px;
  background: var(--clay-tint); color: var(--clay-deep);
  display: grid; place-items: center; font-weight: 700; font-size: 13px;
}
.agent-pop .ap-item.on .ava { background: var(--surface); }
.agent-pop .ap-meta { display: flex; flex-direction: column; min-width: 0; flex: 1; }
.agent-pop .ap-name { font-size: 13.5px; font-weight: 600; color: var(--ink); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.agent-pop .ap-badge {
  font-style: normal; font-size: 10.5px; font-weight: 500; color: var(--clay-deep);
  background: var(--clay-tint); padding: 0 6px; border-radius: 5px; margin-left: 4px;
}
.agent-pop .ap-item.on .ap-badge { background: var(--surface); }
.agent-pop .ap-desc { font-size: 12px; color: var(--muted); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.agent-pop .ap-caps { display: flex; flex-wrap: wrap; gap: 5px; margin-top: 3px; }
.agent-pop .cap {
  font-style: normal; font-size: 11px; color: var(--clay-deep);
  background: var(--clay-tint); padding: 1px 7px; border-radius: 6px;
}
.agent-pop .ap-item.on .cap { background: var(--surface); }
.agent-pop .ap-check { color: var(--clay-deep); flex-shrink: 0; }
.agent-pop .hint { padding: 10px; font-size: 13px; color: var(--muted); }
</style>
