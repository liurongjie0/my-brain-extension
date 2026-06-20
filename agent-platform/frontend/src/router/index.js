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
