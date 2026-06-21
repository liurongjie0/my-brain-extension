import http from './http.js'

export const api = {
  listAgents: () => http.get('/api/agents'),
  listMyConversations: (userId) => http.get('/api/conversations', { params: { userId } }),
  getMessages: (id) => http.get(`/api/conversations/${id}/messages`),
  renameConversation: (id, title) => http.put(`/api/conversations/${id}`, { title }),
  deleteConversation: (id) => http.delete(`/api/conversations/${id}`),

  adminAgents: {
    list: () => http.get('/api/admin/agents'),
    create: (data) => http.post('/api/admin/agents', data),
    update: (id, data) => http.put(`/api/admin/agents/${id}`, data),
    remove: (id) => http.delete(`/api/admin/agents/${id}`),
    getBindings: (id) => http.get(`/api/admin/agents/${id}/bindings`),
    bindings: (id, data) => http.put(`/api/admin/agents/${id}/bindings`, data)
  },
  adminKbs: {
    list: () => http.get('/api/admin/knowledge-bases'),
    create: (data) => http.post('/api/admin/knowledge-bases', data),
    remove: (id) => http.delete(`/api/admin/knowledge-bases/${id}`),
    listDocs: (id) => http.get(`/api/admin/knowledge-bases/${id}/documents`),
    removeDoc: (id, docId) => http.delete(`/api/admin/knowledge-bases/${id}/documents/${docId}`),
    reprocessDoc: (id, docId) => http.post(`/api/admin/knowledge-bases/${id}/documents/${docId}/reprocess`),
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
  },
  adminModels: {
    list: () => http.get('/api/admin/model-configs'),
    create: (data) => http.post('/api/admin/model-configs', data),
    update: (id, data) => http.put(`/api/admin/model-configs/${id}`, data),
    remove: (id) => http.delete(`/api/admin/model-configs/${id}`)
  },
  adminMcp: {
    list: () => http.get('/api/admin/mcp-servers'),
    create: (data) => http.post('/api/admin/mcp-servers', data),
    update: (id, data) => http.put(`/api/admin/mcp-servers/${id}`, data),
    remove: (id) => http.delete(`/api/admin/mcp-servers/${id}`),
    test: (id) => http.post(`/api/admin/mcp-servers/${id}/test`)
  },
  metrics: () => http.get('/api/admin/metrics')
}
