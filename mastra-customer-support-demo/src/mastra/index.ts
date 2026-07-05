import { Mastra } from '@mastra/core';
import { supportAgent } from './agents/support-agent.ts';
import { supportMcpServers } from './advanced/mcp.ts';
import { supportMemory } from './advanced/memory.ts';
import { supportObservability } from './advanced/observability.ts';
import { supportProcessors } from './advanced/processors.ts';
import { supportReplyQualityScorer } from './advanced/scorers.ts';
import { supportStorage } from './advanced/storage.ts';
import { refundTools } from './tools/refund-tools.ts';
import { refundWorkflow } from './workflows/refund-workflow.ts';

export const mastra = new Mastra({
  storage: supportStorage,
  observability: supportObservability,
  agents: { supportAgent },
  tools: refundTools,
  workflows: { refundWorkflow },
  processors: supportProcessors,
  memory: { supportMemory },
  mcpServers: supportMcpServers,
  scorers: { supportReplyQualityScorer },
});
