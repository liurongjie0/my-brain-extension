# Mastra Customer Support Demo Design

## Goal

Build a standalone Mastra TypeScript demo that shows how a customer-support refund agent can combine LLM-facing tools with a deterministic workflow and human approval.

The demo lives at `mastra-customer-support-demo` and does not integrate with the existing Spring/Vue `agent-platform`.

## User Experience

The project supports three local flows:

- `npm run dev` starts Mastra Studio so the user can inspect and run the agent/workflow.
- `npm run demo:auto` runs a low-risk refund request that completes automatically.
- `npm run demo:approval` runs a high-risk refund request that suspends for approval, then resumes with an approved decision.

The demo uses mock orders, mock policy rules, and a mock refund executor. It does not call real Stripe, Zendesk, databases, or payment APIs.

## Architecture

The project is a self-contained TypeScript/Mastra app:

- `src/mastra/index.ts` registers agents, tools, and workflows.
- `src/mastra/agents/support-agent.ts` defines a support agent with concise refund-support instructions.
- `src/mastra/tools/*.ts` expose typed tools for order lookup, policy checking, risk scoring, refund execution, and reply drafting.
- `src/mastra/workflows/refund-workflow.ts` orchestrates the refund process using Mastra workflow steps.
- `src/domain/*.ts` contains pure business logic and mock data that can be tested without invoking a model.
- `scripts/*.ts` contains CLI demos for the automatic and approval paths.

## Data Flow

The refund workflow accepts `orderId`, `customerMessage`, and optional approval data. It performs:

1. Look up the mock order.
2. Evaluate refund policy eligibility.
3. Score refund risk from amount, order status, delivery age, and customer history.
4. Auto-approve low-risk small refunds.
5. Suspend high-risk or high-value requests with review context.
6. Resume after approval and execute a mock refund, or stop if declined.
7. Draft a customer-facing response.

## Human Approval

Human-in-the-loop behavior is demonstrated with Mastra `suspend()` and `resume()`:

- Low-risk refunds return a completed workflow result.
- High-risk refunds return a suspended workflow state containing review details.
- The approval demo script resumes the same run with `{ approved: true, note: ... }`.

## Testing

Tests cover the pure refund policy and approval decision behavior first:

- Small eligible refunds do not require human approval.
- High-value or high-risk refunds require approval.
- Approved requests can produce a mock refund.
- Declined requests do not execute a refund.

The Mastra workflow wiring is verified with TypeScript compilation and local demo scripts.

## Non-Goals

- No real payment provider integration.
- No persistent database.
- No production authentication or authorization.
- No frontend beyond Mastra Studio.
- No integration into the existing `agent-platform`.
