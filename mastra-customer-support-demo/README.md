# Mastra Customer Support Demo

A small Mastra TypeScript demo for a customer-support refund agent.

It shows how to combine:

- typed Mastra tools,
- a deterministic refund workflow,
- mock order/payment data,
- and human-in-the-loop `suspend()` / `resume()` approval.

## Setup

```bash
cd mastra-customer-support-demo
npm install --package-lock=false
```

The CLI workflow demos use only mock data and do not need a model API key.

Mastra Studio and `supportAgent` chat use the configured model. By default this demo uses `deepseek/deepseek-chat`, so set `DEEPSEEK_API_KEY` before asking the agent to generate responses in Studio. Set `SUPPORT_AGENT_MODEL` to switch to another model (for example `openai/gpt-4o-mini`).

## Run

Start Mastra Studio:

```bash
npm run dev
```

Open the Studio URL printed by Mastra, usually `http://localhost:4111`.

Start Mastra Studio with a local Chinese UI overlay:

```bash
npm run dev:zh
```

This injects a best-effort Chinese translation layer into the generated Studio page. It does not modify `node_modules` or committed Mastra build output.

## Advanced Playground

`npm run dev:zh` starts Mastra Studio with the Chinese UI overlay and request-context presets.

The demo registers:

- LibSQL storage at `.mastra/support-demo.db`
- Support Agent memory with working memory
- Persistent observability (traces, metrics, logs) in an embedded DuckDB file
  at `.mastra/support-demo-observability.duckdb` — LibSQL cannot store metrics,
  so the observability domain is composed onto DuckDB
- Secret redaction and token-limit processors
- A local Support Policy MCP server
- A deterministic Support Reply Quality scorer

Run the low-risk automatic refund path:

```bash
npm run demo:auto
```

Expected behavior:

- `ord_small_recent` is inside the refund window.
- Risk is low.
- No human approval is required.
- A mock refund is created.

Run the high-value human approval path:

```bash
npm run demo:approval
```

Expected behavior:

- `ord_high_value_recent` is eligible but high value.
- The workflow suspends at `approve-refund`.
- The script resumes the same run with an approved decision.
- A mock refund is created after approval.

## Multi-Agent Travel Demo

`travel-planner-agent` is a routing (supervisor) agent that plans short hiking
trips near Shanghai by dispatching work to other primitives, multi-turn until
the plan is complete:

- sub-agents: `transport-agent` and `lodging-agent` (each with its own search
  tool over mock data),
- a deterministic `itinerary-workflow` that assembles the day-by-day plan and
  budget once options are chosen — its transport and lodging lookups run
  under `.parallel()`,
- a `compare-trips-workflow` showing advanced control flow: `.foreach()` fans
  out cost calculation per candidate trail (concurrency 3), a ranking step
  sorts them, and `.branch()` picks between a within-budget recommendation
  and an over-budget explanation — ask the planner "预算 1000 从上海出发去哪条线最划算"
  to see it dispatched,
- direct tools: `search-trails` and `build-packing-list`,
- working memory that keeps confirmed choices, so a follow-up like "预算砍到每晚
  150" re-dispatches only the lodging agent and re-runs the workflow,
- a workspace with an Agent Skill (`workspace/skills/hiking-safety/`): the
  planner gets `skill` / `skill_read` / `skill_search` tools and loads the
  safety guidelines on demand (progressive disclosure) instead of carrying
  them in its system prompt,
- RAG over `knowledge/*.md` hiking guides: run `npm run seed:rag` once to
  chunk (per `##` section), embed locally with fastembed (no API key), and
  index into DuckDBVector; the planner queries it via `search-hiking-guides`,
- semantic recall on travel memory: past conversation turns are embedded into
  the same DuckDB vector store and retrieved by similarity, on top of working
  memory and the recent-message window,
- a model fallback chain (`deepseek-chat` with one retry, then
  `deepseek-reasoner`) so transient provider failures do not kill a turn,
- an experimental agent goal: `setObjective()` makes a judge model grade each
  pass until the objective is met (see `npm run demo:advanced`),
- a workspace sandbox (`LocalSandbox`, scratch dir `.mastra/sandbox`): the
  planner gets `execute_command` and file tools and is instructed to verify
  cost arithmetic by running `node -e` in the sandbox; only PATH is inherited
  from the host env, with macOS seatbelt isolation where available — swap in
  `@mastra/docker` / `@mastra/e2b` for hard isolation.

The support agent additionally demonstrates:

- dynamic instructions assembled per request from `requestContext`
  (`customerTier=vip` adds a VIP addendum; `preferredTone` adjusts style) —
  try the request-context presets in Studio,
- consuming an external MCP server: `MCPClient` spawns the support-policy MCP
  server over stdio and merges its `policyDocs_*` tools into the agent's
  toolset at request time.

Structured output and goals demo (requires the model API key):

```bash
npm run demo:advanced
```

Try it in Studio (chat with `Travel Planner Agent` and watch the delegation
in the tool-call panel), or run the scripted two-turn CLI demo (requires the
model API key, `DEEPSEEK_API_KEY` by default; set `TRAVEL_AGENT_MODEL` to
switch models):

```bash
npm run demo:travel
```

## Test

```bash
npm test
npm run typecheck
```

The tests cover the pure refund rules so they run without a model provider.

## Project Map

- `src/domain/orders.ts` contains mock orders.
- `src/domain/refunds.ts` contains refund policy, risk, approval, and mock execution logic.
- `src/domain/refunds.test.ts` covers the core rules.
- `src/mastra/advanced/` configures local storage, memory, observability, processors, MCP, request context, and scorers.
- `src/mastra/schemas.ts` contains the shared zod schemas for tools and workflows.
- `src/mastra/tools/refund-tools.ts` exposes domain functions as Mastra tools.
- `src/mastra/agents/support-agent.ts` defines the support agent.
- `src/mastra/workflows/refund-workflow.ts` defines the suspend/resume refund workflow.
- `src/domain/travel.ts` contains mock trails, transport, lodging, and itinerary logic.
- `src/mastra/travel-schemas.ts` contains the travel zod schemas.
- `src/mastra/tools/travel-tools.ts` exposes travel domain functions as Mastra tools.
- `src/mastra/agents/travel-agents.ts` defines the travel routing agent and its sub-agents.
- `src/mastra/travel-workspace.ts` mounts `workspace/` with the hiking-safety skill.
- `workspace/skills/hiking-safety/` is the Agent Skill (SKILL.md + references).
- `src/mastra/travel-vector.ts` is the shared DuckDB vector store + local embedder.
- `knowledge/` holds the hiking guides; `scripts/seed-knowledge.ts` indexes them.
- `src/mastra/workflows/itinerary-workflow.ts` assembles itineraries deterministically.
- `scripts/demo-auto.ts` runs the automatic path.
- `scripts/demo-approval.ts` runs the approval path.
- `scripts/demo-travel.ts` runs the two-turn multi-agent travel demo.

## Troubleshooting

If a new Studio tab hangs loading forever while `curl http://localhost:4111` responds instantly, close the other `localhost:4111` tabs and reload. Each dev-mode Studio tab holds several persistent connections (hot-reload watcher, polling), and Chrome allows at most 6 HTTP/1.1 connections per host — once older tabs consume the quota, new navigations queue indefinitely.

## Notes

This demo uses mock order/payment data. Do not put real provider credentials into tracked files.
