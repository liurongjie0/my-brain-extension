import { createRouter, createWebHistory } from 'vue-router'
import AdminLayout from '../layouts/AdminLayout.vue'

// User-facing chat is a standalone full-screen app; the admin console lives under
// /admin with its own layout — the two are deliberately kept separate.
const routes = [
  { path: '/', redirect: '/chat' },
  { path: '/chat', component: () => import('../views/chat/ChatView.vue') },
  {
    path: '/admin',
    component: AdminLayout,
    children: [
      { path: '', redirect: '/admin/agents' },
      { path: 'agents', component: () => import('../views/admin/AgentsView.vue') },
      { path: 'models', component: () => import('../views/admin/ModelsView.vue') },
      { path: 'knowledge', component: () => import('../views/admin/KnowledgeView.vue') },
      { path: 'tools', component: () => import('../views/admin/ToolsView.vue') },
      { path: 'mcp', component: () => import('../views/admin/McpView.vue') },
      { path: 'monitor', component: () => import('../views/admin/MonitorView.vue') },
      { path: 'conversations', component: () => import('../views/admin/ConversationsView.vue') }
    ]
  }
]

export default createRouter({ history: createWebHistory(), routes })
