import { describe, expect, it } from 'vitest';
import { supportPolicyMcpServer } from './mcp.ts';

describe('supportPolicyMcpServer', () => {
  it('exposes the support policy MCP tool', async () => {
    const toolList = await supportPolicyMcpServer.getToolListInfo();

    expect(toolList.tools.map((tool) => tool.name)).toContain(
      'summarize-refund-policy',
    );
  });
});
