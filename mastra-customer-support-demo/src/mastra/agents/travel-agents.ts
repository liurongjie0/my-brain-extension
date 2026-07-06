import { Agent } from '@mastra/core/agent';
import { Memory } from '@mastra/memory';
import { createVectorQueryTool } from '@mastra/rag';
import {
  travelEmbedder,
  travelKnowledgeIndexName,
  travelVector,
} from '../travel-vector.ts';
import {
  searchLodgingTool,
  searchTransportTool,
  travelPlannerDirectTools,
} from '../tools/travel-tools.ts';
import { travelWorkspace } from '../travel-workspace.ts';
import { itineraryWorkflow } from '../workflows/itinerary-workflow.ts';

export const defaultTravelAgentModel = 'deepseek/deepseek-chat';

export function resolveTravelAgentModel(
  env: NodeJS.ProcessEnv = process.env,
): string {
  return env.TRAVEL_AGENT_MODEL ?? defaultTravelAgentModel;
}

export const travelAgentModel = resolveTravelAgentModel();

export const travelWorkingMemoryTemplate = `# 旅行规划记忆
- 出发城市:
- 人数:
- 天数:
- 预算约束:
- 已选线路:
- 已选交通:
- 已选住宿:`;

export const travelMemoryOptions = {
  id: 'travel-memory',
  // Local fastembed embeddings + the shared DuckDB vector store let semantic
  // recall search older conversation turns without any embedding API key.
  vector: travelVector,
  embedder: travelEmbedder,
  options: {
    lastMessages: 20,
    semanticRecall: {
      topK: 3,
      messageRange: 2,
      scope: 'resource',
    },
    generateTitle: true,
    workingMemory: {
      enabled: true,
      scope: 'resource',
      template: travelWorkingMemoryTemplate,
    },
  },
} as const;

export const travelMemory = new Memory(travelMemoryOptions);

// RAG: retrieval over knowledge/*.md hiking guides, indexed by
// scripts/seed-knowledge.ts into the shared DuckDB vector store.
export const searchGuidesTool = createVectorQueryTool({
  id: 'search-hiking-guides',
  description:
    'Search the hiking guide knowledge base (seasons, route details, resupply points, hazards) for facts beyond the mock trail data. Chinese queries work best.',
  vectorStore: travelVector,
  indexName: travelKnowledgeIndexName,
  model: travelEmbedder,
});

export const transportAgent = new Agent({
  id: 'transport-agent',
  name: 'Transport Agent',
  description:
    'Transport specialist: finds mock rail/bus options from a departure city to a hiking trail and recommends one, with price and duration trade-offs.',
  instructions: `
You are a transport specialist for short hiking trips.

- Use searchTransportTool with the departure city and trail ID you were given.
- Report every option compactly (mode, departure time, duration, price), then
  recommend one: prefer the cheapest unless the requester asked for speed.
- Always include the option IDs (such as tr_mgs_rail_am) so the planner can
  reference your recommendation later.
- If the search returns no options, say so plainly and suggest the nearest
  supported departure city; never invent schedules or prices.
- Reply in Chinese when the request is in Chinese.
`,
  model: travelAgentModel,
  tools: { searchTransportTool },
});

export const lodgingAgent = new Agent({
  id: 'lodging-agent',
  name: 'Lodging Agent',
  description:
    'Lodging specialist: filters mock lodging near a hiking trail by a per-night budget and recommends where to stay.',
  instructions: `
You are a lodging specialist for short hiking trips.

- Use searchLodgingTool with the trail ID; convert any budget you were given
  into CNY cents for maxPricePerNightCents (¥500 → 50000).
- Report matching options compactly (name, tier, nightly price, distance to
  trailhead) and recommend one, keeping the option IDs (such as lg_mgs_hostel)
  visible for the planner.
- When the result is empty, relay the returned reason and state the cheapest
  available price so the planner can renegotiate the budget; never invent
  lodging or prices.
- Reply in Chinese when the request is in Chinese.
`,
  model: travelAgentModel,
  tools: { searchLodgingTool },
});

// Exported separately so tests can assert the supervisor wiring without
// reaching into Agent internals.
export const travelPlannerSubAgents = { transportAgent, lodgingAgent };
export const travelPlannerWorkflows = { itineraryWorkflow };

// Model fallback chain: retry the primary once, then fail over to the
// reasoner model (same DEEPSEEK_API_KEY) so transient provider errors do not
// kill a routing turn.
export const travelPlannerModelChain = [
  { id: 'primary', model: travelAgentModel, maxRetries: 1 },
  { id: 'fallback', model: 'deepseek/deepseek-reasoner', maxRetries: 1 },
];

export const travelPlannerAgent = new Agent({
  id: 'travel-planner-agent',
  name: 'Travel Planner Agent',
  description:
    'Routing agent that plans short hiking trips by delegating to transport and lodging specialists, deterministic itinerary workflows, and trail tools.',
  instructions: `
You are the routing agent for planning short hiking trips near Shanghai. You
coordinate specialists and never invent trail, transport, lodging, or price
data yourself.

Primitives you can dispatch, and when:
- searchTrailsTool: find or confirm the trail whenever the destination is new
  or ambiguous.
- search-hiking-guides: retrieval over curated hiking guides — use it for
  seasons, route conditions, resupply points, and local tips that the mock
  trail data does not cover; cite what it returns instead of guessing.
- Transport Agent (sub-agent): everything about getting there — pass the
  departure city and trail ID, get back options and a recommendation.
- Lodging Agent (sub-agent): everything about where to stay — pass the trail
  ID and the per-night budget, get back options and a recommendation.
- itineraryWorkflow: run it once transport and lodging are chosen, with the
  chosen option IDs, to assemble the day-by-day plan and budget summary.
- buildPackingListTool: gear list for the trail and season, near the end.
- Workspace skills (skill / skill_read / skill_search tools): load the
  hiking-safety skill before finalizing any plan for a moderate or hard
  trail, or whenever the user asks about safety or is a beginner; base the
  safety section on it instead of general knowledge.

Process:
1. If the departure city, trip length, or budget is missing, ask the user
   instead of guessing.
2. Confirm the trail, then delegate transport and lodging to the specialists.
3. Run itineraryWorkflow with the chosen IDs, then add the packing list.
4. Present the full plan and finish with a short "分派过程" section listing
   which specialist or tool handled each part.

On follow-up changes (for example a lower budget), re-dispatch only the
affected specialist and re-run itineraryWorkflow; keep prior confirmed choices
from working memory. If a specialist reports that nothing fits a constraint,
relay the trade-off to the user and ask how to proceed.

Reply in Chinese when the user writes Chinese.
`,
  model: travelPlannerModelChain,
  // Agent goals (experimental): when a caller sets an objective via
  // setObjective(), this judge model grades each loop pass and the agent
  // keeps working until the objective is met or maxRuns is exhausted.
  goal: { judge: travelAgentModel, maxRuns: 3 },
  // Routing turns fan out into sub-agent calls, workflow runs, and tools, so
  // the loop budget is wider than a single-agent chat.
  defaultOptions: { maxSteps: 15 },
  memory: travelMemory,
  workspace: travelWorkspace,
  agents: travelPlannerSubAgents,
  workflows: travelPlannerWorkflows,
  tools: { ...travelPlannerDirectTools, searchGuidesTool },
});
