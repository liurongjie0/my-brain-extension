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

Mastra Studio and `supportAgent` chat use the configured model. By default this demo uses `deepseek/deepseek-v4-flash`, so set `DEEPSEEK_API_KEY` before asking the agent to generate responses in Studio.

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
- Storage-backed observability traces
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
- `src/mastra/tools/refund-tools.ts` exposes domain functions as Mastra tools.
- `src/mastra/agents/support-agent.ts` defines the support agent.
- `src/mastra/workflows/refund-workflow.ts` defines the suspend/resume refund workflow.
- `scripts/demo-auto.ts` runs the automatic path.
- `scripts/demo-approval.ts` runs the approval path.

## Notes

This demo uses mock order/payment data. Do not put real provider credentials into tracked files.
