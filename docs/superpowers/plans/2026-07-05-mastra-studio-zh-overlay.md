# Mastra Studio Chinese Overlay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `npm run dev:zh` to run Mastra Studio with a local Chinese UI overlay.

**Architecture:** Keep translation data separate from browser DOM mutation code and Node HTML injection code. The dev wrapper starts `mastra dev`, watches generated HTML, and injects a compiled inline module script that translates visible text and common input placeholders at runtime.

**Tech Stack:** TypeScript, Node.js child process/fs APIs, Vitest, browser DOM APIs.

---

## Task 1: Translation Data and Tests

**Files:**
- Create: `mastra-customer-support-demo/src/studio-zh/translations.ts`
- Create: `mastra-customer-support-demo/src/studio-zh/translations.test.ts`

- [ ] Write tests proving common labels translate, unknown text stays unchanged, and placeholders are covered.
- [ ] Run `npm test -- src/studio-zh/translations.test.ts` and verify failure because the module does not exist.
- [ ] Implement the translation map and `translateText()` helper.
- [ ] Run the test and verify it passes.

## Task 2: Overlay and Injection

**Files:**
- Create: `mastra-customer-support-demo/src/studio-zh/overlay.ts`
- Create: `mastra-customer-support-demo/src/studio-zh/injector.ts`
- Create: `mastra-customer-support-demo/src/studio-zh/injector.test.ts`
- Modify: `mastra-customer-support-demo/package.json`

- [ ] Write tests proving the injector inserts one overlay script and does not duplicate it.
- [ ] Run the injector test and verify failure because the module does not exist.
- [ ] Implement browser overlay translation and Node HTML injector.
- [ ] Add `build:studio-zh` script with `tsc` and `dev:zh` wrapper.
- [ ] Run tests and typecheck.

## Task 3: Dev Wrapper and Verification

**Files:**
- Create: `mastra-customer-support-demo/scripts/dev-zh.ts`
- Modify: `mastra-customer-support-demo/README.md`

- [ ] Implement the wrapper that starts `mastra dev` and reinjects after generated HTML changes.
- [ ] Document `npm run dev:zh`.
- [ ] Restart Studio with `npm run dev:zh`.
- [ ] Verify the in-app browser shows Chinese sidebar and chat labels.
