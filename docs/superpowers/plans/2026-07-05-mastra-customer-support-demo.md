# Mastra Customer Support Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a standalone Mastra TypeScript customer-support refund demo with automatic and human-approved refund flows.

**Architecture:** Create a new `mastra-customer-support-demo` project with pure domain logic, Mastra tools, a support agent, a refund workflow, and CLI scripts. Keep payment/order systems mocked so the demo runs locally without external business APIs.

**Tech Stack:** TypeScript, Node.js, Mastra, Zod, Vitest.

---

## File Structure

- Create `mastra-customer-support-demo/package.json` for scripts and dependencies.
- Create `mastra-customer-support-demo/tsconfig.json` for ES module TypeScript.
- Create `mastra-customer-support-demo/vitest.config.ts` for tests.
- Create `mastra-customer-support-demo/src/domain/orders.ts` for mock order data and lookup.
- Create `mastra-customer-support-demo/src/domain/refunds.ts` for policy, risk, approval, and mock refund logic.
- Create `mastra-customer-support-demo/src/domain/refunds.test.ts` for TDD coverage of core behavior.
- Create `mastra-customer-support-demo/src/mastra/tools/refund-tools.ts` for typed Mastra tools.
- Create `mastra-customer-support-demo/src/mastra/agents/support-agent.ts` for the support agent.
- Create `mastra-customer-support-demo/src/mastra/workflows/refund-workflow.ts` for the workflow and suspend/resume approval step.
- Create `mastra-customer-support-demo/src/mastra/index.ts` to register the Mastra app.
- Create `mastra-customer-support-demo/scripts/demo-auto.ts` and `scripts/demo-approval.ts` for local demos.
- Create `mastra-customer-support-demo/README.md` with setup and run commands.

## Task 1: Project Scaffolding

- [ ] Create the project folder and config files.
- [ ] Add scripts: `dev`, `test`, `typecheck`, `demo:auto`, `demo:approval`.
- [ ] Install dependencies.
- [ ] Run `npm test` and expect no tests found or a failing missing-test state until Task 2 adds tests.

## Task 2: Domain Logic With TDD

- [ ] Write failing tests in `src/domain/refunds.test.ts` for auto approval, required approval, approved refund execution, and declined refund blocking.
- [ ] Run `npm test` and verify the tests fail because domain functions do not exist.
- [ ] Implement mock orders and refund domain logic.
- [ ] Run `npm test` and verify the domain tests pass.

## Task 3: Mastra Tools and Agent

- [ ] Create typed Mastra tools with `createTool()` for lookup, policy, risk, refund execution, and response drafting.
- [ ] Create `supportAgent` with refund-support instructions and the tools object.
- [ ] Register the agent in `src/mastra/index.ts`.
- [ ] Run `npm run typecheck` and fix type errors.

## Task 4: Refund Workflow and CLI Demos

- [ ] Create `refundWorkflow` with lookup, assessment, approval, execution, and reply steps.
- [ ] Use `suspend()` when approval is required and `resume()` in the approval demo.
- [ ] Register the workflow in `src/mastra/index.ts`.
- [ ] Add `demo:auto` and `demo:approval` scripts that print readable JSON output.
- [ ] Run both demo scripts and verify the expected automatic and suspended/resumed paths.

## Task 5: Documentation and Final Verification

- [ ] Write `README.md` with setup, API-key note, scripts, and expected demo behavior.
- [ ] Run `npm test`.
- [ ] Run `npm run typecheck`.
- [ ] Run `npm run demo:auto`.
- [ ] Run `npm run demo:approval`.
- [ ] Run `git status --short` and summarize only files changed for this demo.
