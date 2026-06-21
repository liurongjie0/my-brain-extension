<script setup>
import { ref, onMounted, nextTick, computed } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../../api/index.js'
import { streamChat } from '../../api/sse.js'
import { useUserStore } from '../../stores/user.js'
import { renderMarkdown } from '../../utils/markdown.js'
import ToolTrace from '../../components/ToolTrace.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  SendHorizontal, Square, Search, Plus, MoreHorizontal, Pencil, Trash2, ChevronDown, Check, Menu,
  Settings2, Bot, MessageCircle, BookOpen, Wrench, Workflow, Sparkles, Lightbulb, MessageSquare,
  ListTodo, Circle, CircleDot, CircleCheck
} from 'lucide-vue-next'

const router = useRouter()
// agent identity icon by type (replaces the single-character avatar)
const agentTypeIcon = { chat: MessageCircle, rag: BookOpen, tool: Wrench, react: Workflow }
function agentIcon(type) { return agentTypeIcon[type] || Bot }
// rotating icons for the recommendation pills on the empty state
const recoIcons = [Sparkles, Lightbulb, Workflow, Search]

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
const historyDrawer = ref(false)

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

async function loadAgents() { agents.value = (await api.listAgents()) || [] }
async function loadConversations() { conversations.value = (await api.listMyConversations(userId)) || [] }

function selectAgent(a) {
  resetStream()
  currentAgent.value = a
  conversationId.value = null
  messages.value = []
  agentMenu.value = false
  agentQuery.value = ''
  nextTick(() => composer.value && composer.value.focus())
}
function newChat() {
  resetStream()
  conversationId.value = null
  messages.value = []
  nextTick(() => composer.value && composer.value.focus())
}
async function openConversation(c) {
  resetStream()
  conversationId.value = c.id
  currentAgent.value = agents.value.find((a) => a.id === c.agentId) || currentAgent.value
  messages.value = []
  const history = (await api.getMessages(c.id)) || []
  messages.value = history.map((m) => {
    const steps = parseSteps(m.toolCalls)
    return {
      role: m.role, content: m.content, steps,
      blocks: m.role === 'assistant' ? blocksFromHistory(m.content, steps) : [],
      sources: [], error: ''
    }
  })
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
// plan content is a JSON array of { content, status } todos
const pickPlan = (data) => { const c = pick(data); try { const a = JSON.parse(c); return Array.isArray(a) ? a : [] } catch (_) { return [] } }

// An assistant turn is an ordered list of blocks so narration, tool calls and the
// final answer render as one interleaved timeline (说一句话 → 调工具 → 再说一句话),
// instead of a tool box pinned above a single text blob.
//   { t: 'text',  v: '...' }        streamed prose (narration or final answer)
//   { t: 'tools', v: [step, ...] }  a contiguous run of tool calls
//   { t: 'think', v: '...' }        collapsed reasoning (e.g. DeepSeek reasoning_content)
function lastBlock(m) { return m.blocks[m.blocks.length - 1] }
function pushText(m, s) {
  const b = lastBlock(m)
  if (b && b.t === 'text') b.v += s
  else m.blocks.push({ t: 'text', v: s })
}
function pushTool(m, step) {
  const b = lastBlock(m)
  if (b && b.t === 'tools') b.v.push(step)
  else m.blocks.push({ t: 'tools', v: [step] })
}
function pushThink(m, s) {
  const b = lastBlock(m)
  if (b && b.t === 'think') b.v += s
  else m.blocks.push({ t: 'think', v: s })
}
// write_todos resends the whole list each time — update the plan card in place
function pushPlan(m, todos) {
  const b = m.blocks.find((x) => x.t === 'plan')
  if (b) b.v = todos
  else m.blocks.push({ t: 'plan', v: todos })
}
const planIcon = (status) => status === 'completed' ? CircleCheck : (status === 'in_progress' ? CircleDot : Circle)
// rebuild blocks for a persisted assistant message: plan card, tool trace, then the answer
function blocksFromHistory(content, steps) {
  const blocks = []
  const toolSteps = []
  let plan = null
  for (const s of (steps || [])) {
    if (s && s.tool === 'write_todos') plan = s.plan || []
    else toolSteps.push(s)
  }
  if (plan) blocks.push({ t: 'plan', v: plan })
  if (toolSteps.length) blocks.push({ t: 'tools', v: toolSteps })
  if (content) blocks.push({ t: 'text', v: content })
  return blocks
}

function useExample(t) { input.value = t; nextTick(() => composer.value && composer.value.focus()) }

// abort any in-flight stream and unlock the composer — called when the context changes
function resetStream() {
  if (abort) { abort.abort(); abort = null }
  sending.value = false
}
function finalize(localAbort) {
  if (abort === localAbort) { abort = null; sending.value = false }
}
function onEnter(e) {
  // ignore Enter while an IME candidate is being composed (Chinese/Japanese/Korean input)
  if (e.isComposing || e.keyCode === 229) return
  e.preventDefault()
  send()
}

async function send() {
  if (!currentAgent.value || !input.value.trim() || sending.value) return
  const text = input.value.trim()
  input.value = ''
  messages.value.push({ role: 'user', content: text, blocks: [], steps: [] })
  messages.value.push({ role: 'assistant', content: '', blocks: [], steps: [], sources: [], error: '' })
  const assistant = messages.value[messages.value.length - 1]
  const localAbort = new AbortController()
  abort = localAbort
  sending.value = true
  scrollToBottom()
  // ignore events from a stream that has been superseded by an agent/conversation switch
  const live = () => abort === localAbort && !localAbort.signal.aborted
  await streamChat(
    { agentId: currentAgent.value.id, conversationId: conversationId.value, message: text, userId },
    {
      signal: localAbort.signal,
      onEvent: (e) => {
        if (!live()) return
        if (e.event === 'meta') { try { conversationId.value = JSON.parse(e.data).conversationId } catch (_) {} }
        else if (e.event === 'token') { const t = pick(e.data); pushText(assistant, t); assistant.content += t }
        else if (e.event === 'think') pushThink(assistant, pick(e.data))
        else if (e.event === 'plan') pushPlan(assistant, pickPlan(e.data))
        else if (e.event === 'step') pushTool(assistant, pickStep(e.data))
        else if (e.event === 'source') assistant.sources.push(pick(e.data))
        else if (e.event === 'error') assistant.error = pick(e.data)
        scrollToBottom()
      },
      onError: (err) => { if (!live()) return; assistant.error = err.message || '连接出错，请重试'; finalize(localAbort) },
      onDone: () => { if (abort !== localAbort) return; finalize(localAbort); loadConversations(); scrollToBottom() }
    }
  )
}
function stop() { resetStream() }

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
          role="button" tabindex="0" :aria-current="conversationId === c.id ? 'true' : undefined"
          @click="openConversation(c)"
          @keydown.enter.prevent="openConversation(c)"
          @keydown.space.prevent="openConversation(c)"
        >
          <MessageSquare class="conv-ic" :size="15" :stroke-width="1.8" aria-hidden="true" />
          <div class="conv-main">
            <span class="conv-t">{{ c.title || '未命名会话' }}</span>
            <span class="conv-sub">{{ agentName(c.agentId) }} · {{ relTime(c.updatedAt) }}</span>
          </div>
          <el-dropdown
            trigger="click" placement="bottom-end"
            @command="(cmd) => cmd === 'rename' ? renameConversation(c) : removeConversation(c)"
          >
            <button type="button" class="conv-more" aria-label="会话操作" @click.stop><MoreHorizontal :size="16" /></button>
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
      <router-link to="/admin" class="admin-entry">
        <Settings2 :size="16" :stroke-width="1.9" aria-hidden="true" /> 管理后台
      </router-link>
    </aside>

    <section class="main">
      <header class="topbar">
        <button class="nav-trigger" type="button" aria-label="会话列表" @click="historyDrawer = true">
          <Menu :size="18" :stroke-width="2" />
        </button>
        <el-popover
          v-model:visible="agentMenu" trigger="click" :width="300"
          placement="bottom-start" popper-class="agent-pop" :teleported="true"
        >
          <template #reference>
            <button class="switcher" aria-haspopup="listbox" :aria-expanded="agentMenu">
              <template v-if="currentAgent">
                <span class="ava sm"><component :is="agentIcon(currentAgent.agentType)" :size="14" :stroke-width="1.9" /></span>
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
                <span class="ava sm"><component :is="agentIcon(a.agentType)" :size="15" :stroke-width="1.9" /></span>
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
          <h1 class="hi">Hi，今天从哪里开始</h1>
          <p class="hi-sub">{{ currentAgent ? `「${currentAgent.name}」已就绪，点击下方推荐或直接提问` : '选择一个助手，或点击下方推荐开始' }}</p>
          <div class="recos">
            <template v-if="currentAgent">
              <button v-for="(ex, i) in examples" :key="ex" class="reco" @click="useExample(ex)">
                <component :is="recoIcons[i % recoIcons.length]" class="reco-ic" :size="17" :stroke-width="1.8" />
                <span class="reco-tx"><span class="reco-main">{{ ex }}</span></span>
              </button>
            </template>
            <template v-else>
              <button v-for="a in agents" :key="a.id" class="reco" @click="selectAgent(a)">
                <component :is="agentIcon(a.agentType)" class="reco-ic" :size="17" :stroke-width="1.8" />
                <span class="reco-tx">
                  <span class="reco-main">{{ a.name }}</span>
                  <span class="reco-sub">{{ a.description || typeLabel[a.agentType] + ' 助手' }}</span>
                </span>
              </button>
              <div v-if="!agents.length" class="hint">还没有启用的 Agent</div>
            </template>
          </div>
        </div>

        <div v-else class="thread">
          <div v-for="(m, i) in messages" :key="i" class="row" :class="m.role">
            <template v-if="m.role === 'assistant'">
              <span class="ava sm a-ava"><component :is="agentIcon(currentAgent && currentAgent.agentType)" :size="14" :stroke-width="1.9" /></span>
              <div class="bubble a-bubble">
                <template v-for="(b, bi) in m.blocks" :key="bi">
                  <ToolTrace v-if="b.t === 'tools'" :steps="b.v" class="blk" />
                  <div v-else-if="b.t === 'plan'" class="plan blk">
                    <div class="plan-head">
                      <ListTodo :size="14" :stroke-width="2" aria-hidden="true" /> 任务清单
                      <span class="plan-count">{{ b.v.filter((t) => t.status === 'completed').length }}/{{ b.v.length }}</span>
                    </div>
                    <ul class="plan-list">
                      <li v-for="(td, ti) in b.v" :key="ti" class="plan-item" :class="td.status">
                        <component :is="planIcon(td.status)" class="pi" :size="15" :stroke-width="2" aria-hidden="true" />
                        <span class="pt">{{ td.content }}</span>
                      </li>
                    </ul>
                  </div>
                  <details v-else-if="b.t === 'think'" class="think blk">
                    <summary class="think-sum"><Sparkles :size="13" :stroke-width="2" aria-hidden="true" /> 已深度思考</summary>
                    <div class="think-body">{{ b.v }}</div>
                  </details>
                  <div v-else class="text md blk" v-html="renderMarkdown(b.v)"></div>
                </template>
                <div v-if="!m.blocks.length && i === messages.length - 1 && sending && !m.error" class="text md typing">思考中…</div>
                <div v-if="m.error" class="err" role="alert">{{ m.error }}</div>
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
            :placeholder="currentAgent ? '有问题，尽管问，Shift+回车换行' : '请先选择一个 Agent'"
            :disabled="!currentAgent || sending" rows="2"
            @keydown.enter.exact="onEnter"
          />
          <div class="composer-bar">
            <span class="cb-sp"></span>
            <button v-if="sending" class="send stop" @click="stop"><Square :size="14" :stroke-width="2.2" /> 停止</button>
            <button v-else class="send" :disabled="!currentAgent || !input.trim()" aria-label="发送" @click="send">
              <SendHorizontal :size="18" :stroke-width="2" />
            </button>
          </div>
        </div>
      </div>

      <!-- narrow-screen access to history (the .history rail is hidden under 760px) -->
      <el-drawer v-model="historyDrawer" direction="ltr" :with-header="false" size="272px" :append-to-body="false">
        <div class="drawer-history">
          <button class="new-btn" @click="newChat(); historyDrawer = false"><Plus :size="16" :stroke-width="2" /> 新对话</button>
          <div class="h-title">我的会话</div>
          <div class="conv-list">
            <div
              v-for="c in conversations" :key="c.id"
              class="conv" :class="{ on: conversationId === c.id }"
              role="button" tabindex="0"
              @click="openConversation(c); historyDrawer = false"
              @keydown.enter.prevent="openConversation(c); historyDrawer = false"
            >
              <div class="conv-main">
                <span class="conv-t">{{ c.title || '未命名会话' }}</span>
                <span class="conv-sub">{{ agentName(c.agentId) }} · {{ relTime(c.updatedAt) }}</span>
              </div>
            </div>
            <div v-if="!conversations.length" class="hint">暂无历史</div>
          </div>
        </div>
      </el-drawer>
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
.conv-list { flex: 1; display: flex; flex-direction: column; gap: 1px; overflow: auto; }
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
.conv-ic { flex-shrink: 0; color: var(--muted); }
.conv.on .conv-ic { color: var(--clay); }
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
.conv:focus-visible { outline: 2px solid var(--clay); outline-offset: -2px; }
.conv-more {
  display: flex; align-items: center; justify-content: center;
  opacity: 0; color: var(--muted); padding: 2px; border-radius: 5px;
  border: none; background: transparent; cursor: pointer;
}
.conv:hover .conv-more, .conv-more:focus-visible { opacity: 1; }
.conv-more:hover { background: var(--surface-2); color: var(--ink); }
.hint { padding: 10px; font-size: 13px; color: var(--muted); }
.admin-entry {
  display: flex; align-items: center; gap: 8px; flex-shrink: 0;
  margin-top: 10px; padding: 9px 11px; border-radius: 9px;
  border: 1px solid transparent; text-decoration: none;
  color: var(--ink-soft); font-size: 13px; font-weight: 500;
  transition: background 0.14s ease, color 0.14s ease;
}
.admin-entry:hover { background: var(--surface); color: var(--clay-deep); }

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
.ava.lg { width: 42px; height: 42px; border-radius: 12px; }

/* ---- stream ---- */
.stream { flex: 1; overflow: auto; }
.welcome { max-width: 580px; margin: 0 auto; padding: 16vh 24px 40px; }
.hi { text-align: center; font-size: 27px; font-weight: 600; letter-spacing: -0.012em; color: var(--ink); }
.hi-sub { text-align: center; margin: 11px 0 30px; font-size: 14px; color: var(--muted); }

.recos { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.reco {
  display: flex; align-items: center; gap: 11px;
  padding: 13px 15px; border: 1px solid var(--line); border-radius: 12px;
  background: var(--surface); cursor: pointer; font: inherit; text-align: left;
  transition: border-color 0.15s ease, background 0.15s ease, transform 0.15s ease, box-shadow 0.2s ease;
}
.reco:hover { border-color: #c6cef5; background: var(--clay-tint); transform: translateY(-1px); box-shadow: var(--shadow-sm); }
.reco-ic { flex-shrink: 0; color: var(--clay); }
.reco-tx { min-width: 0; display: flex; flex-direction: column; }
.reco-main {
  font-size: 13.5px; color: var(--ink); font-weight: 500;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.reco-sub {
  font-size: 11.5px; color: var(--muted); margin-top: 2px;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}

@media (max-width: 560px) { .recos { grid-template-columns: 1fr; } }

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
/* blocks form an interleaved timeline: narration → tools → narration → answer */
.blk + .blk { margin-top: 14px; }
.text { line-height: var(--lh-read); color: var(--ink); font-size: var(--fs-read); }
.typing { color: var(--muted); }

/* collapsed reasoning ("已深度思考"), dimmed and out of the way until expanded */
.think { border: 1px solid var(--line); border-radius: 10px; background: var(--surface-2); overflow: hidden; }
.think-sum {
  display: flex; align-items: center; gap: 7px; cursor: pointer; user-select: none; list-style: none;
  padding: 7px 12px; font-size: 12.5px; color: var(--muted);
}
.think-sum::-webkit-details-marker { display: none; }
.think-sum::marker { content: ''; }
.think[open] .think-sum { border-bottom: 1px solid var(--line); }
.think-body {
  padding: 10px 12px; font-size: 12.5px; line-height: 1.7; color: var(--ink-soft);
  white-space: pre-wrap; word-break: break-word; font-family: var(--font-mono);
}

/* task checklist card (write_todos), updates in place as the agent works */
.plan { border: 1px solid var(--line); border-radius: 10px; background: var(--surface); overflow: hidden; }
.plan-head {
  display: flex; align-items: center; gap: 7px;
  padding: 8px 12px; background: var(--surface-2); border-bottom: 1px solid var(--line);
  font-size: 12.5px; font-weight: 600; color: var(--ink);
}
.plan-count { margin-left: auto; font-weight: 500; color: var(--muted); font-feature-settings: "tnum" 1; }
.plan-list { list-style: none; margin: 0; padding: 6px 0; }
.plan-item { display: flex; align-items: flex-start; gap: 9px; padding: 5px 12px; font-size: 13.5px; line-height: 1.5; }
.plan-item .pi { flex-shrink: 0; margin-top: 1px; color: var(--muted); }
.plan-item .pt { color: var(--ink-soft); }
.plan-item.in_progress .pi { color: var(--clay); }
.plan-item.in_progress .pt { color: var(--ink); font-weight: 500; }
.plan-item.completed .pi { color: #2f9e6f; }
.plan-item.completed .pt { color: var(--muted); text-decoration: line-through; }

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
.composer { padding: 8px 24px 22px; }
.composer-inner {
  max-width: 720px; margin: 0 auto; display: flex; flex-direction: column;
  background: var(--surface); border: 1px solid var(--line); border-radius: 18px;
  padding: 12px 14px 10px; box-shadow: var(--shadow-md);
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
}
.composer-inner:focus-within { border-color: var(--clay); box-shadow: var(--shadow-md), 0 0 0 3px rgba(76, 102, 224, 0.1); }
.composer-input {
  width: 100%; border: none; background: transparent; resize: none; outline: none;
  font: inherit; font-size: 15px; line-height: 1.6; color: var(--ink);
  max-height: 200px; min-height: 44px; padding: 2px 2px 0;
}
.composer-input::placeholder { color: var(--muted); }
.composer-bar { display: flex; align-items: center; margin-top: 6px; }
.cb-sp { flex: 1; }
.send {
  flex-shrink: 0; display: inline-flex; align-items: center; justify-content: center; gap: 5px;
  width: 36px; height: 36px; border: none; border-radius: 50%;
  background: var(--clay); color: #fff; cursor: pointer; font: inherit; font-weight: 600;
  transition: opacity 0.14s ease, background 0.14s ease;
}
.send:disabled { opacity: 0.35; cursor: not-allowed; }
.send.stop {
  width: auto; padding: 0 14px; border-radius: 18px;
  background: var(--surface-2); color: var(--ink); border: 1px solid var(--line);
}

.nav-trigger {
  display: none; align-items: center; justify-content: center;
  width: 34px; height: 34px; flex-shrink: 0;
  border: 1px solid var(--line); border-radius: 9px;
  background: var(--surface); color: var(--ink-soft); cursor: pointer;
}
.nav-trigger:hover { border-color: var(--clay); color: var(--clay-deep); }
.drawer-history { display: flex; flex-direction: column; height: 100%; padding: 6px 8px; }
.drawer-history .conv-list { overflow: auto; }

@media (max-width: 760px) {
  .history { display: none; }
  .nav-trigger { display: inline-flex; }
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
