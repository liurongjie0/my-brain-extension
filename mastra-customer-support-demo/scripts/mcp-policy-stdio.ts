// Stdio entry point for the support-policy MCP server, so it can be consumed
// as an *external* MCP server by MCPClient (see advanced/mcp-client.ts).
// The same server is also served over HTTP by the Mastra dev server.
import { supportPolicyMcpServer } from '../src/mastra/advanced/mcp.ts';

await supportPolicyMcpServer.startStdio();
