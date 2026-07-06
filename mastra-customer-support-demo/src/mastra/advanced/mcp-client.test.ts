import { afterAll, describe, expect, it } from 'vitest';
import { supportMcpClient } from './mcp-client.ts';

afterAll(async () => {
  await supportMcpClient.disconnect();
});

describe('supportMcpClient', () => {
  it(
    'consumes the policy MCP server over stdio and exposes namespaced tools',
    { timeout: 30_000 },
    async () => {
      const tools = await supportMcpClient.listTools();
      const toolNames = Object.keys(tools);
      const policyToolName = toolNames.find(
        (name) => name.startsWith('policyDocs') && name.includes('summarize-refund-policy'),
      );
      expect(policyToolName, `tools: ${toolNames.join(', ')}`).toBeDefined();

      const policyTool = tools[policyToolName!];
      const execute = policyTool.execute;
      expect(execute).toBeDefined();

      const result = (await execute!(
        { locale: 'zh-CN' } as never,
        {} as never,
      )) as { content?: Array<{ type: string; text?: string }> } | { summary: string };

      expect(JSON.stringify(result)).toContain('30 天');
    },
  );
});
