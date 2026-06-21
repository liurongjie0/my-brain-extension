<script setup>
import { ref, computed } from 'vue'
import { Wrench, CircleCheck, CircleX, ChevronRight } from 'lucide-vue-next'

// Renders a ReAct tool-call trajectory as a two-level drill-down:
// collapsed summary -> tool list -> per-tool args/result. `steps` items may be
// structured objects ({tool, args, result, ok}) or legacy pre-joined strings.
const props = defineProps({ steps: { type: Array, default: () => [] } })

const open = ref(false)
const expanded = ref(new Set())

const items = computed(() => props.steps.map((s) => {
  if (s && typeof s === 'object') {
    return { tool: s.tool || 'tool', args: s.args, result: s.result, ok: s.ok !== false }
  }
  return { tool: null, raw: String(s), ok: true }
}))
const allOk = computed(() => items.value.every((i) => i.ok))

function toggleItem(i) {
  const set = new Set(expanded.value)
  set.has(i) ? set.delete(i) : set.add(i)
  expanded.value = set
}

function fmt(v) {
  if (v == null || v === '') return ''
  if (typeof v === 'object') {
    try { return JSON.stringify(v, null, 2) } catch (_) { return String(v) }
  }
  const s = String(v)
  try { return JSON.stringify(JSON.parse(s), null, 2) } catch (_) { return s }
}
</script>

<template>
  <div v-if="items.length" class="trace">
    <button class="trace-head" @click="open = !open">
      <Wrench class="lead" :size="15" :stroke-width="2" />
      <span class="ttl">已调用 {{ items.length }} 个工具</span>
      <component :is="allOk ? CircleCheck : CircleX" class="st" :class="allOk ? 'ok' : 'bad'" :size="15" :stroke-width="2" />
      <span class="sp" />
      <ChevronRight class="chev" :class="{ open }" :size="15" :stroke-width="2.2" />
    </button>

    <div v-if="open" class="trace-body">
      <div v-for="(it, i) in items" :key="i" class="tool">
        <button class="tool-head" :class="{ flat: !it.tool }" @click="it.tool && toggleItem(i)">
          <Wrench class="ti" :size="14" :stroke-width="2" />
          <span v-if="it.tool" class="nm">{{ it.tool }}</span>
          <span v-else class="raw">{{ it.raw }}</span>
          <span class="sp" />
          <component :is="it.ok ? CircleCheck : CircleX" class="st sm" :class="it.ok ? 'ok' : 'bad'" :size="14" :stroke-width="2" />
          <ChevronRight v-if="it.tool" class="chev sm" :class="{ open: expanded.has(i) }" :size="14" :stroke-width="2.2" />
        </button>
        <div v-if="it.tool && expanded.has(i)" class="tool-detail">
          <div v-if="fmt(it.args)" class="kv">
            <div class="lbl">参数</div>
            <pre class="code">{{ fmt(it.args) }}</pre>
          </div>
          <div class="kv">
            <div class="lbl">返回</div>
            <pre class="code">{{ fmt(it.result) || '(空)' }}</pre>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.trace {
  border: 1px solid var(--line);
  border-radius: 10px;
  overflow: hidden;
  background: var(--surface);
}
.trace-head {
  width: 100%; display: flex; align-items: center; gap: 8px;
  padding: 8px 12px; border: none; background: var(--surface-2);
  cursor: pointer; font: inherit;
}
.trace-head .lead { font-size: 15px; color: var(--muted); }
.trace-head .ttl { font-size: 13px; font-weight: 600; color: var(--ink); }
.sp { flex: 1; }
.st { font-size: 15px; }
.st.sm { font-size: 13px; }
.st.ok { color: #2f9e6f; }
.st.bad { color: #c2563c; }
.chev { font-size: 12px; color: var(--muted); transition: transform 0.15s ease; }
.chev.sm { font-size: 11px; }
.chev.open { transform: rotate(90deg); }

.trace-body { border-top: 1px solid var(--line); }
.tool + .tool .tool-head { border-top: 1px solid var(--line); }
.tool-head {
  width: 100%; display: flex; align-items: center; gap: 8px;
  padding: 8px 12px; border: none; background: transparent;
  cursor: pointer; font: inherit; text-align: left;
}
.tool-head.flat { cursor: default; }
.tool-head:not(.flat):hover { background: var(--surface-2); }
.tool-head .ti { font-size: 14px; color: var(--clay); }
.tool-head .nm { font-size: 13px; font-weight: 600; color: var(--ink); font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }
.tool-head .raw { font-size: 12.5px; color: var(--ink-soft); word-break: break-word; }

.tool-detail { padding: 2px 12px 12px 32px; display: flex; flex-direction: column; gap: 10px; }
.kv .lbl { font-size: 11px; letter-spacing: 0.04em; color: var(--muted); margin-bottom: 4px; }
.code {
  margin: 0; font-size: 12px; line-height: 1.55;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  background: var(--surface-2); border: 1px solid var(--line);
  border-radius: 8px; padding: 8px 10px;
  white-space: pre-wrap; word-break: break-word; color: var(--ink);
}
</style>
