// Minimal demo MCP server over the legacy HTTP+SSE transport (matches Spring AI's
// HttpClientSseClientTransport). Exposes a couple of trivial tools for end-to-end testing.
import express from 'express'
import { z } from 'zod'
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js'
import { SSEServerTransport } from '@modelcontextprotocol/sdk/server/sse.js'

const PORT = process.env.PORT || 8765

const server = new McpServer({ name: 'demo-mcp', version: '1.0.0' })

server.tool(
  'add',
  '计算两个数字之和',
  { a: z.number().describe('第一个加数'), b: z.number().describe('第二个加数') },
  async ({ a, b }) => ({ content: [{ type: 'text', text: String(a + b) }] })
)

server.tool(
  'now',
  '返回服务器当前时间',
  {},
  async () => ({ content: [{ type: 'text', text: new Date().toISOString() }] })
)

const app = express()
const transports = {}

app.get('/sse', async (req, res) => {
  const transport = new SSEServerTransport('/messages', res)
  transports[transport.sessionId] = transport
  res.on('close', () => { delete transports[transport.sessionId] })
  await server.connect(transport)
})

app.post('/messages', async (req, res) => {
  const sessionId = req.query.sessionId
  const transport = transports[sessionId]
  if (transport) {
    await transport.handlePostMessage(req, res)
  } else {
    res.status(400).send('no transport for sessionId')
  }
})

app.listen(PORT, () => {
  console.log(`demo MCP server (SSE) on http://localhost:${PORT}/sse`)
})
