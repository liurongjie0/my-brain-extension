import { Mastra } from '@mastra/core';
import { MastraEditor } from '@mastra/editor';
import { supportAgent } from './agents/support-agent.ts';
import {
  lodgingAgent,
  transportAgent,
  travelMemory,
  travelPlannerAgent,
} from './agents/travel-agents.ts';
import { supportMcpServers } from './advanced/mcp.ts';
import { supportMemory } from './advanced/memory.ts';
import { supportObservability } from './advanced/observability.ts';
import { supportProcessors } from './advanced/processors.ts';
import { supportReplyQualityScorer } from './advanced/scorers.ts';
import { supportStorage } from './advanced/storage.ts';
import { refundTools } from './tools/refund-tools.ts';
import { travelTools } from './tools/travel-tools.ts';
import { travelVector } from './travel-vector.ts';
import { itineraryWorkflow } from './workflows/itinerary-workflow.ts';
import { refundWorkflow } from './workflows/refund-workflow.ts';

export const mastra = new Mastra({
  editor: new MastraEditor(),
  storage: supportStorage,
  observability: supportObservability,
  // The travel sub-agents are also registered top-level so Studio can chat
  // with each specialist directly, not only through the routing agent.
  agents: { supportAgent, travelPlannerAgent, transportAgent, lodgingAgent },
  tools: { ...refundTools, ...travelTools },
  workflows: { refundWorkflow, itineraryWorkflow },
  processors: supportProcessors,
  memory: { supportMemory, travelMemory },
  vectors: { travelVector },
  mcpServers: supportMcpServers,
  scorers: { supportReplyQualityScorer },
});
