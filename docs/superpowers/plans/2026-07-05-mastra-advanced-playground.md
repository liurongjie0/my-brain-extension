# Mastra Advanced Playground Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable a local advanced Mastra playground for the customer-support demo with persistent storage, memory, traces, request-context presets, processors, MCP, and a simple scorer.

**Architecture:** Keep the existing refund agent/workflow intact and add focused advanced modules under `mastra-customer-support-demo/src/mastra/advanced/`. Wire those modules through the existing Mastra registry and the Chinese dev launcher. Use official Mastra packages where available and deterministic tests for the locally owned behavior.

**Tech Stack:** TypeScript, Mastra, `@mastra/libsql`, `@mastra/observability`, `@mastra/memory`, `@mastra/mcp`, Vitest, local SQLite/LibSQL files under `.mastra/`.

---

## File Structure

- Modify `mastra-customer-support-demo/package.json` to add advanced Mastra packages.
- Create `mastra-customer-support-demo/src/mastra/advanced/storage.ts` for the local LibSQL store.
- Create `mastra-customer-support-demo/src/mastra/advanced/memory.ts` for support-agent thread memory.
- Create `mastra-customer-support-demo/src/mastra/advanced/observability.ts` for storage-backed traces.
- Create `mastra-customer-support-demo/src/mastra/advanced/processors.ts` for redaction and token-limit processors.
- Create `mastra-customer-support-demo/src/mastra/advanced/scorers.ts` for a deterministic support-quality scorer.
- Create `mastra-customer-support-demo/src/mastra/advanced/mcp.ts` for a local support policy MCP server.
- Create `mastra-customer-support-demo/request-context-presets.json` for Studio request-context presets.
- Modify `mastra-customer-support-demo/src/mastra/agents/support-agent.ts` to attach memory and processors.
- Modify `mastra-customer-support-demo/src/mastra/index.ts` to register storage, observability, processors, scorer, memory, and MCP server.
- Modify `mastra-customer-support-demo/scripts/dev-zh.ts` to pass the request-context presets file to `mastra dev`.
- Add focused Vitest coverage under `mastra-customer-support-demo/src/mastra/advanced/*.test.ts` and `mastra-customer-support-demo/scripts/dev-zh.test.ts`.

## Task 1: Add Advanced Dependencies

**Files:**
- Modify: `mastra-customer-support-demo/package.json`
- Create/Modify: `mastra-customer-support-demo/package-lock.json`

- [ ] **Step 1: Install official advanced packages**

Run:

```bash
cd /Users/liurongjie/my-brain-extension/mastra-customer-support-demo
npm install @mastra/libsql@latest @mastra/observability@latest @mastra/memory@latest @mastra/mcp@latest
```

Expected: `package.json` includes those four dependencies and npm exits with code 0.

- [ ] **Step 2: Run existing tests**

Run:

```bash
cd /Users/liurongjie/my-brain-extension/mastra-customer-support-demo
npm test
```

Expected: existing tests pass before feature code starts.

- [ ] **Step 3: Commit dependency baseline**

Run:

```bash
cd /Users/liurongjie/my-brain-extension
git add mastra-customer-support-demo/package.json mastra-customer-support-demo/package-lock.json
git commit -m "Add Mastra advanced playground dependencies"
```

Expected: commit succeeds and only dependency files are staged.

## Task 2: Add Storage, Memory, Observability, and Request Context Config

**Files:**
- Create: `mastra-customer-support-demo/src/mastra/advanced/storage.ts`
- Create: `mastra-customer-support-demo/src/mastra/advanced/memory.ts`
- Create: `mastra-customer-support-demo/src/mastra/advanced/observability.ts`
- Create: `mastra-customer-support-demo/src/mastra/advanced/config.test.ts`
- Create: `mastra-customer-support-demo/request-context-presets.json`

- [ ] **Step 1: Write the failing config test**

Create `src/mastra/advanced/config.test.ts`:

```typescript
import { describe, expect, it } from 'vitest';
import { supportMemory, supportMemoryOptions } from './memory.ts';
import {
  supportObservability,
  supportObservabilityRequestContextKeys,
  supportObservabilityServiceName,
} from './observability.ts';
import { supportStorage, supportStorageUrl } from './storage.ts';

describe('advanced Mastra config', () => {
  it('uses a local ignored LibSQL file for durable demo data', () => {
    expect(supportStorageUrl).toBe('file:./.mastra/support-demo.db');
    expect(supportStorage).toBeDefined();
  });

  it('enables conversational memory with working-memory support', () => {
    expect(supportMemory.id).toBe('support-memory');
    expect(supportMemoryOptions.options?.lastMessages).toBe(20);
    expect(supportMemoryOptions.options?.workingMemory).toMatchObject({
      enabled: true,
      scope: 'resource',
    });
  });

  it('configures storage-backed observability for support traces', () => {
    expect(supportObservabilityServiceName).toBe('mastra-customer-support-demo');
    expect(supportObservabilityRequestContextKeys).toEqual([
      'locale',
      'customerTier',
      'tenantId',
      'supportChannel',
    ]);
    expect(supportObservability).toBeDefined();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd /Users/liurongjie/my-brain-extension/mastra-customer-support-demo
npx vitest run src/mastra/advanced/config.test.ts
```

Expected: FAIL because the advanced config modules do not exist yet.

- [ ] **Step 3: Implement storage, memory, and observability modules**

Create `src/mastra/advanced/storage.ts`:

```typescript
import { LibSQLStore } from '@mastra/libsql';

export const supportStorageUrl = 'file:./.mastra/support-demo.db';

export const supportStorage = new LibSQLStore({
  id: 'support-demo-storage',
  url: supportStorageUrl,
});
```

Create `src/mastra/advanced/memory.ts`:

```typescript
import { Memory } from '@mastra/memory';

export const supportWorkingMemoryTemplate = `# 客户支持记忆
- 客户偏好:
- 最近订单:
- 风险备注:
- 人工审批备注:`;

export const supportMemoryOptions = {
  id: 'support-memory',
  options: {
    lastMessages: 20,
    semanticRecall: false,
    generateTitle: true,
    workingMemory: {
      enabled: true,
      scope: 'resource',
      template: supportWorkingMemoryTemplate,
    },
  },
} satisfies ConstructorParameters<typeof Memory>[0];

export const supportMemory = new Memory(supportMemoryOptions);
```

Create `src/mastra/advanced/observability.ts`:

```typescript
import {
  MastraStorageExporter,
  Observability,
  SamplingStrategyType,
} from '@mastra/observability';

export const supportObservabilityServiceName = 'mastra-customer-support-demo';

export const supportObservabilityRequestContextKeys = [
  'locale',
  'customerTier',
  'tenantId',
  'supportChannel',
];

export const supportObservability = new Observability({
  configs: {
    default: {
      serviceName: supportObservabilityServiceName,
      sampling: { type: SamplingStrategyType.ALWAYS },
      exporters: [
        new MastraStorageExporter({
          maxBatchSize: 1,
          maxBatchWaitMs: 250,
        }),
      ],
      requestContextKeys: supportObservabilityRequestContextKeys,
    },
  },
});
```

- [ ] **Step 4: Add request-context presets JSON**

Create `request-context-presets.json`:

```json
{
  "中文 VIP 客户": {
    "locale": "zh-CN",
    "customerTier": "vip",
    "tenantId": "demo-store-cn",
    "supportChannel": "studio",
    "preferredTone": "concise"
  },
  "中文普通客户": {
    "locale": "zh-CN",
    "customerTier": "standard",
    "tenantId": "demo-store-cn",
    "supportChannel": "studio",
    "preferredTone": "friendly"
  },
  "English Escalation": {
    "locale": "en-US",
    "customerTier": "vip",
    "tenantId": "demo-store-us",
    "supportChannel": "email",
    "preferredTone": "precise"
  }
}
```

- [ ] **Step 5: Run config test to verify it passes**

Run:

```bash
cd /Users/liurongjie/my-brain-extension/mastra-customer-support-demo
npx vitest run src/mastra/advanced/config.test.ts
```

Expected: PASS.

## Task 3: Add Processors, Scorer, and MCP Server

**Files:**
- Create: `mastra-customer-support-demo/src/mastra/advanced/processors.ts`
- Create: `mastra-customer-support-demo/src/mastra/advanced/scorers.ts`
- Create: `mastra-customer-support-demo/src/mastra/advanced/mcp.ts`
- Create: `mastra-customer-support-demo/src/mastra/advanced/scorers.test.ts`
- Create: `mastra-customer-support-demo/src/mastra/advanced/mcp.test.ts`

- [ ] **Step 1: Write failing scorer and MCP tests**

Create `src/mastra/advanced/scorers.test.ts`:

```typescript
import { describe, expect, it } from 'vitest';
import { supportReplyQualityScorer } from './scorers.ts';

describe('supportReplyQualityScorer', () => {
  it('rewards concrete and safe refund replies', async () => {
    const result = await supportReplyQualityScorer.run({
      input: '用户询问 ord_small_recent 是否可以退款',
      output:
        '已查询订单 ord_small_recent，符合退款政策，退款已处理。金额会回到原支付方式。',
    });

    expect(result.score).toBeGreaterThanOrEqual(0.75);
    expect(result.reason).toContain('具体');
  });

  it('penalizes vague promises', async () => {
    const result = await supportReplyQualityScorer.run({
      input: '用户要求退款',
      output: '一定没问题，我保证全部立刻退给你。',
    });

    expect(result.score).toBeLessThan(0.75);
    expect(result.reason).toContain('缺少');
  });
});
```

Create `src/mastra/advanced/mcp.test.ts`:

```typescript
import { describe, expect, it } from 'vitest';
import { supportPolicyMcpServer } from './mcp.ts';

describe('supportPolicyMcpServer', () => {
  it('exposes the support policy MCP tool', async () => {
    const toolList = await supportPolicyMcpServer.getToolListInfo();

    expect(toolList.tools.map((tool) => tool.name)).toContain(
      'summarize-refund-policy',
    );
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
cd /Users/liurongjie/my-brain-extension/mastra-customer-support-demo
npx vitest run src/mastra/advanced/scorers.test.ts src/mastra/advanced/mcp.test.ts
```

Expected: FAIL because the scorer and MCP modules do not exist yet.

- [ ] **Step 3: Implement processors, scorer, and MCP modules**

Create `src/mastra/advanced/processors.ts`:

```typescript
import {
  RegexFilterProcessor,
  TokenLimiterProcessor,
} from '@mastra/core/processors/processors';

export const supportSecretRedactionProcessor = new RegexFilterProcessor({
  presets: ['secrets'],
  strategy: 'redact',
  phase: 'all',
});

export const supportTokenLimiterProcessor = new TokenLimiterProcessor({
  limit: 4000,
  strategy: 'truncate',
  countMode: 'cumulative',
});

export const supportInputProcessors = [
  supportSecretRedactionProcessor,
  supportTokenLimiterProcessor,
];

export const supportOutputProcessors = [
  supportSecretRedactionProcessor,
  supportTokenLimiterProcessor,
];

export const supportProcessors = {
  supportSecretRedactionProcessor,
  supportTokenLimiterProcessor,
};
```

Create `src/mastra/advanced/scorers.ts`:

```typescript
import { createScorer } from '@mastra/core/evals';

function stringifyScorerValue(value: unknown): string {
  if (typeof value === 'string') {
    return value;
  }

  return JSON.stringify(value);
}

export const supportReplyQualityScorer = createScorer({
  id: 'support-reply-quality',
  name: 'Support Reply Quality',
  description:
    'Scores whether a customer-support reply is concrete, policy-aware, and avoids unsafe refund promises.',
})
  .generateScore(({ run }) => {
    const text = stringifyScorerValue(run.output);
    const lowerText = text.toLowerCase();
    let score = 0;

    if (/退款|refund|订单|order|ord_/.test(lowerText)) {
      score += 0.35;
    }

    if (/已处理|processed|人工|审批|review|policy|政策/.test(lowerText)) {
      score += 0.35;
    }

    if (!/保证|一定|guarantee|立刻全部/.test(lowerText)) {
      score += 0.3;
    }

    return Number(Math.min(score, 1).toFixed(2));
  })
  .generateReason(({ score }) => {
    if (score >= 0.75) {
      return '回复具体，提到了订单或退款状态，并且没有做不安全承诺。';
    }

    return '缺少订单、政策或处理状态等具体信息，或包含过度承诺。';
  });
```

Create `src/mastra/advanced/mcp.ts`:

```typescript
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
    summarizeRefundPolicyMcpTool,
  },
});

export const supportMcpServers = {
  supportPolicyMcpServer,
};
```

- [ ] **Step 4: Run scorer and MCP tests to verify they pass**

Run:

```bash
cd /Users/liurongjie/my-brain-extension/mastra-customer-support-demo
npx vitest run src/mastra/advanced/scorers.test.ts src/mastra/advanced/mcp.test.ts
```

Expected: PASS.

## Task 4: Wire Advanced Features into Agent, Mastra, and Chinese Launcher

**Files:**
- Modify: `mastra-customer-support-demo/src/mastra/agents/support-agent.ts`
- Modify: `mastra-customer-support-demo/src/mastra/index.ts`
- Modify: `mastra-customer-support-demo/scripts/dev-zh.ts`
- Create: `mastra-customer-support-demo/scripts/dev-zh.test.ts`

- [ ] **Step 1: Write failing launcher test**

Create `scripts/dev-zh.test.ts`:

```typescript
import { describe, expect, it } from 'vitest';
import { buildMastraDevArgs } from './dev-zh.ts';

describe('buildMastraDevArgs', () => {
  it('starts Mastra dev with request context presets', () => {
    expect(buildMastraDevArgs()).toEqual([
      'mastra',
      'dev',
      '--request-context-presets',
      'request-context-presets.json',
    ]);
  });
});
```

- [ ] **Step 2: Run launcher test to verify it fails**

Run:

```bash
cd /Users/liurongjie/my-brain-extension/mastra-customer-support-demo
npx vitest run scripts/dev-zh.test.ts
```

Expected: FAIL because `buildMastraDevArgs` is not exported yet.

- [ ] **Step 3: Attach memory and processors to the support agent**

Modify `src/mastra/agents/support-agent.ts`:

```typescript
import { Agent } from '@mastra/core/agent';
import { supportMemory } from '../advanced/memory.ts';
import {
  supportInputProcessors,
  supportOutputProcessors,
} from '../advanced/processors.ts';
import { refundTools } from '../tools/refund-tools.ts';

export const supportAgent = new Agent({
  id: 'support-agent',
  name: 'Support Agent',
  description: 'Customer-support agent for mock refund requests.',
  instructions: `
You are a careful customer-support agent for refund requests.

When a customer asks for a refund:
- Look up the order before making a decision.
- Check policy eligibility and refund risk.
- Never claim that a refund happened unless the refund tool returns a succeeded refund.
- If a refund needs approval, explain that a human review is required.
- Keep customer replies concise, calm, and specific.
- Prefer Chinese when the user writes Chinese or requestContext.locale is zh-CN.
`,
  model: 'deepseek/deepseek-v4-flash',
  memory: supportMemory,
  inputProcessors: supportInputProcessors,
  outputProcessors: supportOutputProcessors,
  maxProcessorRetries: 1,
  tools: refundTools,
});
```

- [ ] **Step 4: Register advanced modules in Mastra**

Modify `src/mastra/index.ts`:

```typescript
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
```

- [ ] **Step 5: Refactor `dev-zh.ts` so tests can import it and Studio receives presets**

Modify `scripts/dev-zh.ts` to export `buildMastraDevArgs` and guard process startup:

```typescript
import { spawn } from 'node:child_process';
import { copyFile, mkdir, readFile, stat, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { build } from 'esbuild';
import { injectChineseOverlay } from '../src/studio-zh/injector.ts';

const projectRoot = resolve(import.meta.dirname, '..');
const generatedHtmlPath = resolve(projectRoot, '.mastra/output/studio/index.html');
const overlaySourcePath = resolve(projectRoot, 'src/studio-zh/overlay.ts');
const overlayRuntimePath = resolve(projectRoot, '.mastra/zh-overlay.mjs');
const overlayRuntimeOutputPath = resolve(
  projectRoot,
  '.mastra/output/studio/zh-overlay.mjs',
);
const overlayRuntimePublicPath = '/zh-overlay.mjs';

export function buildMastraDevArgs(
  presetsPath = 'request-context-presets.json',
): string[] {
  return ['mastra', 'dev', '--request-context-presets', presetsPath];
}

async function pathExists(path: string): Promise<boolean> {
  try {
    await stat(path);
    return true;
  } catch {
    return false;
  }
}

async function buildOverlayRuntime(): Promise<void> {
  await mkdir(dirname(overlayRuntimePath), { recursive: true });
  await build({
    entryPoints: [overlaySourcePath],
    bundle: true,
    format: 'esm',
    outfile: overlayRuntimePath,
    platform: 'browser',
    sourcemap: false,
    logLevel: 'silent',
  });
}

async function injectOverlay(): Promise<void> {
  if (!(await pathExists(generatedHtmlPath))) {
    return;
  }

  await buildOverlayRuntime();
  await copyFile(overlayRuntimePath, overlayRuntimeOutputPath);

  const html = await readFile(generatedHtmlPath, 'utf8');
  const injected = injectChineseOverlay(
    html,
    `import '${overlayRuntimePublicPath}';`,
  );

  if (injected !== html) {
    await writeFile(generatedHtmlPath, injected, 'utf8');
    console.log('[studio-zh] Chinese overlay injected.');
  }
}

export function startChineseMastraDev(): void {
  const child = spawn('npx', buildMastraDevArgs(), {
    cwd: projectRoot,
    env: process.env,
    stdio: 'inherit',
  });

  let interval: NodeJS.Timeout | undefined;

  function stop(signal: NodeJS.Signals): void {
    if (interval) {
      clearInterval(interval);
    }
    child.kill(signal);
  }

  process.on('SIGINT', () => stop('SIGINT'));
  process.on('SIGTERM', () => stop('SIGTERM'));

  interval = setInterval(() => {
    injectOverlay().catch((error: unknown) => {
      console.error('[studio-zh] Failed to inject overlay:', error);
    });
  }, 1000);

  child.on('exit', (code, signal) => {
    if (interval) {
      clearInterval(interval);
    }

    if (signal) {
      process.kill(process.pid, signal);
    }

    process.exit(code ?? 0);
  });
}

if (fileURLToPath(import.meta.url) === resolve(process.argv[1] ?? '')) {
  startChineseMastraDev();
}
```

- [ ] **Step 6: Run launcher test to verify it passes**

Run:

```bash
cd /Users/liurongjie/my-brain-extension/mastra-customer-support-demo
npx vitest run scripts/dev-zh.test.ts
```

Expected: PASS.

## Task 5: Full Verification and Local Restart

**Files:**
- Modify: `mastra-customer-support-demo/README.md`

- [ ] **Step 1: Update README advanced section**

Add a short advanced features section to `README.md`:

```markdown
## Advanced playground

`npm run dev:zh` starts Mastra Studio with Chinese UI overlay and request-context presets.

The demo registers:

- LibSQL storage at `.mastra/support-demo.db`
- Support Agent memory with working memory
- Storage-backed observability traces
- Secret redaction and token-limit processors
- A local Support Policy MCP server
- A deterministic Support Reply Quality scorer
```

- [ ] **Step 2: Run all tests**

Run:

```bash
cd /Users/liurongjie/my-brain-extension/mastra-customer-support-demo
npm test
```

Expected: all Vitest suites pass.

- [ ] **Step 3: Run typecheck**

Run:

```bash
cd /Users/liurongjie/my-brain-extension/mastra-customer-support-demo
npm run typecheck
```

Expected: TypeScript exits with code 0.

- [ ] **Step 4: Restart Chinese Studio**

Stop any previous local `mastra dev` process, then run:

```bash
cd /Users/liurongjie/my-brain-extension/mastra-customer-support-demo
npm run dev:zh
```

Expected: Studio is available at `http://localhost:4111`, and the console does not print a config or type error.

- [ ] **Step 5: Verify Studio endpoints**

Run while the dev server is up:

```bash
curl -sS -o /dev/null -w "%{http_code}\n" http://localhost:4111/agents
curl -sS -o /dev/null -w "%{http_code}\n" http://localhost:4111/zh-overlay.mjs
curl -sS http://localhost:4111/api/observability/traces
```

Expected: the first two commands print `200`; the traces command returns valid JSON.

- [ ] **Step 6: Commit implementation**

Run:

```bash
cd /Users/liurongjie/my-brain-extension
git add mastra-customer-support-demo docs/superpowers/plans/2026-07-05-mastra-advanced-playground.md
git commit -m "Enable Mastra advanced playground"
```

Expected: commit succeeds and does not include `.mastra/` or API keys.

## Self-Review

- Spec coverage: storage, memory, observability, request context, processors, MCP, scorer, tests, and local restart all map to tasks above.
- Placeholder scan: no `TBD`, `TODO`, or deferred implementation placeholders are present.
- Type consistency: module names in tests match the exports in implementation steps; `supportMemory`, `supportProcessors`, `supportMcpServers`, and `supportReplyQualityScorer` are the values registered in `src/mastra/index.ts`.
