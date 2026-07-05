# Mastra Advanced Playground Design

## Goal

Turn the existing customer-support refund demo into a local “advanced playground” that exposes the main Mastra Studio advanced areas with useful demo data and behavior.

The playground should let the user try memory, traces, processors, request context, MCP, and simple evaluation without replacing the existing refund agent/workflow.

## Scope

Implement the recommended A option:

- Local persistent storage so conversations, workflow state, traces, and Studio metadata survive restarts.
- Agent memory so the Support Agent can keep thread history and the Studio no longer shows “Memory not enabled”.
- Observability/tracing so new agent and workflow test runs appear under the observability views.
- Request context presets for common demo personas such as Chinese VIP customer and regular customer.
- A demo processor that makes advanced processing visible, with conservative behavior suitable for support workflows.
- A local demo MCP server with a small support-oriented tool so the MCP Servers section has a concrete entry.
- A minimal scorer/evaluation example for customer support response quality.

## Non-Goals

- Do not build a production integration with real Stripe, Shopify, or Zendesk APIs.
- Do not store the user’s DeepSeek API key in tracked files.
- Do not rewrite the refund workflow or the existing Chinese Studio overlay.
- Do not attempt to fully productize Datasets, Experiments, and Workspaces beyond the minimum needed for a Studio demo. Those areas can be expanded later.

## Architecture

The project keeps the existing Mastra entrypoint at `mastra-customer-support-demo/src/mastra/index.ts` as the registry boundary. New advanced features are added as separate modules under `src/mastra/` and wired into that entrypoint.

Expected modules:

- `storage`: creates a local Mastra-compatible SQLite/LibSQL store under `.mastra/`.
- `observability`: configures storage-backed trace export.
- `memory`: configures thread memory for the support agent.
- `processors`: contains the support demo input/output processor.
- `mcp`: contains a local support demo MCP server.
- `scorers`: contains a simple deterministic support-quality scorer where possible.
- `request-context`: defines presets used by the Chinese dev launcher.

The Chinese startup script remains the main launch path. It will continue injecting the translation overlay and will also pass any supported request-context preset configuration to Mastra Studio.

## User Experience

After starting with `npm run dev:zh`, the user should be able to:

- Open the Support Agent and see memory-enabled conversations.
- Send a new test request and then inspect its trace in Observability.
- Open Tools and see the refund tools already present.
- Open MCP Servers and see at least one local demo MCP server if the installed Mastra version supports local server registration in Studio.
- Select or use request context presets when testing supported endpoints or Studio flows.
- Run a simple eval/scorer path and see a support-quality example rather than an empty feature area.

## Data Flow

For a normal agent test:

1. The user sends a message in Studio.
2. The support agent receives request context, memory, and processors.
3. The agent calls existing refund tools when needed.
4. Observability records model/tool/processor spans.
5. Memory writes the conversation into local storage.
6. Scorers can evaluate the final reply when invoked through Studio or a demo script.

For a high-value refund workflow:

1. The workflow looks up the order and assesses risk.
2. The approval step suspends when manual review is required.
3. Storage preserves the suspended run.
4. A resume action continues the workflow.
5. Observability records each workflow step.

## Error Handling

- Missing `DEEPSEEK_API_KEY` should continue to show a clear provider error in Studio; the project will not hardcode the key.
- If an optional advanced package is unavailable or its API differs, the implementation should fail typecheck and be adjusted before completion.
- MCP support should degrade gracefully: if local registration is unsupported by this Mastra version, keep the demo MCP server script and document the limitation.
- Storage files under `.mastra/` remain ignored by git.

## Testing

Verification should include:

- Existing refund-domain tests still pass.
- New storage/observability/config helper tests pass where practical.
- TypeScript typecheck passes.
- The Chinese dev server starts.
- `GET /zh-overlay.mjs` still works.
- A new agent request produces trace data in the observability endpoint when observability is successfully configured.

## Risks

- Mastra APIs for observability, MCP registration, storage, scorers, and request-context presets may differ across the currently installed latest packages.
- Some Studio areas may require persisted editor metadata or cloud/platform features and may not fully populate from local code registration alone.
- DeepSeek provider failures can prevent model spans from completing, but setup/config spans should still be testable.

## Acceptance Criteria

- The advanced playground starts through `npm run dev:zh`.
- Memory is enabled for the Support Agent.
- New requests are visible in observability after the advanced configuration is active.
- The project has at least one demo processor and one demo scorer registered if supported by the installed APIs.
- The project includes a local demo MCP path or a documented fallback if Studio cannot register it locally.
- No API key is committed.
