import { MCPServer } from '@mastra/mcp';
import { createTool } from '@mastra/core/tools';
import { z } from 'zod';

export const summarizeRefundPolicyMcpTool = createTool({
  id: 'summarize-refund-policy',
  description: 'Summarize the demo refund policy for support operators.',
  inputSchema: z.object({
    locale: z.enum(['zh-CN', 'en-US']).default('zh-CN'),
  }),
  outputSchema: z.object({
    summary: z.string(),
  }),
  execute: async ({ locale }) => {
    if (locale === 'en-US') {
      return {
        summary:
          'Delivered orders are refundable within 30 days. High-value or risky refunds require human approval.',
      };
    }

    return {
      summary:
        '已送达订单 30 天内可退款。高金额或高风险退款需要人工审批后再处理。',
    };
  },
});

export const supportPolicyMcpServer = new MCPServer({
  id: 'support-policy-mcp',
  name: 'Support Policy MCP',
  version: '1.0.0',
  description: 'Local MCP server exposing demo customer-support policy tools.',
  tools: {
    'summarize-refund-policy': summarizeRefundPolicyMcpTool,
  },
});

export const supportMcpServers = {
  supportPolicyMcpServer,
};
