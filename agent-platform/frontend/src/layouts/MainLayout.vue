<script setup>
import { useRoute } from 'vue-router'
const route = useRoute()
</script>

<template>
  <div class="shell">
    <aside class="rail">
      <div class="brand">
        <span class="mark">A</span>
        <span class="brand-serif name">Agent 平台</span>
      </div>

      <div class="nav-group">
        <div class="nav-label">使用</div>
        <el-menu :default-active="route.path" router class="rail-menu">
          <el-menu-item index="/chat">
            <span class="dot" />对话
          </el-menu-item>
        </el-menu>
      </div>

      <div class="nav-group">
        <div class="nav-label">管理</div>
        <el-menu :default-active="route.path" router class="rail-menu">
          <el-menu-item index="/admin/agents"><span class="dot" />Agent</el-menu-item>
          <el-menu-item index="/admin/models"><span class="dot" />模型</el-menu-item>
          <el-menu-item index="/admin/knowledge"><span class="dot" />知识库</el-menu-item>
          <el-menu-item index="/admin/tools"><span class="dot" />工具</el-menu-item>
          <el-menu-item index="/admin/mcp"><span class="dot" />MCP</el-menu-item>
          <el-menu-item index="/admin/monitor"><span class="dot" />监控</el-menu-item>
          <el-menu-item index="/admin/conversations"><span class="dot" />会话审计</el-menu-item>
        </el-menu>
      </div>

      <div class="rail-foot">内部 Agent 工作台</div>
    </aside>

    <main class="canvas">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.shell {
  display: flex;
  height: 100vh;
  background: var(--canvas);
}

.rail {
  width: 232px;
  flex-shrink: 0;
  background: var(--sand);
  border-right: 1px solid var(--sand-deep);
  display: flex;
  flex-direction: column;
  padding: 22px 16px 16px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 4px 8px 22px;
}
.brand .mark {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: var(--clay);
  color: #fff;
  display: grid;
  place-items: center;
  font-weight: 700;
  font-size: 18px;
  box-shadow: 0 2px 8px rgba(194, 105, 63, 0.35);
}
.brand .name {
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 0.02em;
  color: var(--ink);
}

.nav-group { margin-bottom: 18px; }
.nav-label {
  font-size: 11.5px;
  letter-spacing: 0.12em;
  color: var(--muted);
  padding: 0 12px 8px;
  text-transform: uppercase;
}

.rail-menu {
  --el-menu-bg-color: transparent;
  --el-menu-text-color: var(--ink-soft);
  --el-menu-hover-bg-color: rgba(194, 105, 63, 0.08);
  --el-menu-active-color: var(--clay-deep);
  --el-menu-item-height: 40px;
}
.rail-menu :deep(.el-menu-item) {
  border-radius: 9px;
  margin: 2px 0;
  font-size: 14.5px;
  font-weight: 500;
}
.rail-menu :deep(.el-menu-item.is-active) {
  background: var(--surface);
  color: var(--clay-deep);
  box-shadow: 0 1px 2px rgba(60, 50, 38, 0.06);
}
.rail-menu :deep(.el-menu-item .dot) {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  opacity: 0.45;
  margin-right: 10px;
}
.rail-menu :deep(.el-menu-item.is-active .dot) { opacity: 1; }

.rail-foot {
  margin-top: auto;
  padding: 12px;
  font-size: 12px;
  color: var(--muted);
  border-top: 1px solid var(--sand-deep);
}

.canvas {
  flex: 1;
  min-width: 0;
  overflow: auto;
}
</style>
