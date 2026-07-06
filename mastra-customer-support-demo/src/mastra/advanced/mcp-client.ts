import { fileURLToPath } from 'node:url';
import { MCPClient } from '@mastra/mcp';

const stdioEntry = fileURLToPath(
  new URL('../../../scripts/mcp-policy-stdio.ts', import.meta.url),
);

// MCP *client* demo — the flip side of registering an MCPServer: consume an
// external MCP server's tools inside an agent. Here the "external" server is
// our own support-policy server spawned over stdio, so the demo needs no
// network or API key; swap the config for any real server (filesystem, maps,
// web search, ...) to pull in third-party tools the same way.
export const supportMcpClient = new MCPClient({
  id: 'support-mcp-client',
  servers: {
    policyDocs: {
      command: 'npx',
      args: ['tsx', stdioEntry],
    },
  },
  timeout: 30_000,
});
