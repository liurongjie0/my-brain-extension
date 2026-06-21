<script setup>
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import {
  MessageCircle, Bot, Cpu, BookOpen, Wrench,
  Plug, Activity, ScrollText, PanelLeftClose, PanelLeftOpen
} from 'lucide-vue-next'

const route = useRoute()
// remember collapsed state across visits — chat is a focused mode, admins keep it expanded
const collapsed = ref(localStorage.getItem('nav-collapsed') === '1')
function toggle() {
  collapsed.value = !collapsed.value
  localStorage.setItem('nav-collapsed', collapsed.value ? '1' : '0')
}

const useNav = [{ path: '/chat', label: '对话', icon: MessageCircle }]
const adminNav = [
  { path: '/admin/agents', label: 'Agent', icon: Bot },
  { path: '/admin/models', label: '模型', icon: Cpu },
  { path: '/admin/knowledge', label: '知识库', icon: BookOpen },
  { path: '/admin/tools', label: '工具', icon: Wrench },
  { path: '/admin/mcp', label: 'MCP', icon: Plug },
  { path: '/admin/monitor', label: '监控', icon: Activity },
  { path: '/admin/conversations', label: '会话审计', icon: ScrollText }
]
const isActive = (p) => route.path === p
</script>

<template>
  <div class="shell" :class="{ collapsed }">
    <aside class="rail">
      <div class="brand">
        <span class="mark">A</span>
        <span v-if="!collapsed" class="brand-serif name">Agent 平台</span>
      </div>

      <div class="nav-group">
        <div v-if="!collapsed" class="nav-label">使用</div>
        <el-tooltip
          v-for="it in useNav" :key="it.path"
          :content="it.label" placement="right" :disabled="!collapsed" :offset="14"
        >
          <router-link :to="it.path" class="nav-item" :class="{ on: isActive(it.path) }">
            <component :is="it.icon" class="ni" :size="19" :stroke-width="1.9" />
            <span v-if="!collapsed" class="nl">{{ it.label }}</span>
          </router-link>
        </el-tooltip>
      </div>

      <div class="nav-group">
        <div v-if="!collapsed" class="nav-label">管理</div>
        <el-tooltip
          v-for="it in adminNav" :key="it.path"
          :content="it.label" placement="right" :disabled="!collapsed" :offset="14"
        >
          <router-link :to="it.path" class="nav-item" :class="{ on: isActive(it.path) }">
            <component :is="it.icon" class="ni" :size="19" :stroke-width="1.9" />
            <span v-if="!collapsed" class="nl">{{ it.label }}</span>
          </router-link>
        </el-tooltip>
      </div>

      <button class="rail-toggle" @click="toggle" :title="collapsed ? '展开导航' : '收起导航'">
        <component :is="collapsed ? PanelLeftOpen : PanelLeftClose" :size="18" :stroke-width="1.9" />
        <span v-if="!collapsed">收起</span>
      </button>
    </aside>

    <main class="canvas">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.shell { display: flex; height: 100vh; background: var(--canvas); }

.rail {
  width: 232px;
  flex-shrink: 0;
  background: var(--sand);
  border-right: 1px solid var(--sand-deep);
  display: flex;
  flex-direction: column;
  padding: 20px 14px 14px;
  transition: width 0.18s ease, padding 0.18s ease;
}
.shell.collapsed .rail { width: 68px; padding: 20px 10px 14px; }

.brand {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 4px 6px 22px;
}
.brand .mark {
  width: 34px; height: 34px; flex-shrink: 0;
  border-radius: 11px; background: var(--clay); color: #fff;
  display: grid; place-items: center; font-weight: 600; font-size: 18px;
  box-shadow: 0 4px 12px -2px rgba(76, 102, 224, 0.42);
}
.brand .name {
  font-size: var(--fs-h2); font-weight: 600; letter-spacing: 0.01em; color: var(--ink);
  white-space: nowrap;
}

.nav-group { margin-bottom: 16px; }
.nav-label {
  font-size: 11.5px; letter-spacing: 0.12em; color: var(--muted);
  padding: 0 12px 8px; text-transform: uppercase;
}

.nav-item {
  display: flex; align-items: center; gap: 11px;
  height: 40px; margin: 2px 0; padding: 0 12px;
  border-radius: 9px; color: var(--ink-soft);
  font-size: 14.5px; font-weight: 500; text-decoration: none;
  transition: background 0.14s ease, color 0.14s ease;
}
.nav-item:hover { background: rgba(76, 102, 224, 0.09); color: var(--ink); }
.nav-item.on {
  background: var(--surface); color: var(--clay-deep);
  box-shadow: 0 1px 2px rgba(60, 50, 38, 0.06);
}
.nav-item .ni { flex-shrink: 0; }
.nav-item .nl { white-space: nowrap; }
.shell.collapsed .nav-item { justify-content: center; padding: 0; gap: 0; }

.rail-toggle {
  margin-top: auto;
  display: flex; align-items: center; gap: 10px; justify-content: center;
  height: 38px; border: 1px solid var(--sand-deep); border-radius: 9px;
  background: transparent; color: var(--muted); cursor: pointer;
  font: inherit; font-size: 13px;
  transition: background 0.14s ease, color 0.14s ease;
}
.rail-toggle:hover { background: var(--surface); color: var(--ink); }

.canvas { flex: 1; min-width: 0; overflow: auto; }
</style>
