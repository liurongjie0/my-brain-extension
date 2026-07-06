// Advanced-features demo: structured output and agent goals (experimental).
// Requires a model API key (DEEPSEEK_API_KEY by default).
import { z } from 'zod';
import { mastra } from '../src/mastra/index.ts';

const agent = mastra.getAgent('travelPlannerAgent');

// ---------------------------------------------------------------------------
// 1) Structured output: parse a free-form request into a typed trip order.
// ---------------------------------------------------------------------------
console.log('=== 结构化输出：自由文本 → 类型化旅行需求单 ===\n');

const tripRequestSchema = z.object({
  trailKeyword: z.string().describe('目的地或线路关键词'),
  departureCity: z.string(),
  days: z.number().int().positive(),
  partySize: z.number().int().positive(),
  lodgingBudgetPerNightYuan: z
    .number()
    .nullable()
    .describe('每晚住宿预算（元），未提及为 null'),
});

const userMessage =
  '下个月我们仨想从杭州出发走徽杭古道，玩两天，每人每晚住宿别超过 200 块。';
console.log(`用户: ${userMessage}\n`);

const parsed = await agent.generate(userMessage, {
  structuredOutput: { schema: tripRequestSchema },
  memory: { thread: 'demo-structured-thread', resource: 'demo-advanced-user' },
  maxSteps: 1,
});

console.log('解析结果:', JSON.stringify(parsed.object, null, 2));

// ---------------------------------------------------------------------------
// 2) Agent goals (experimental): set an objective, let the judge decide when
//    the agent is actually done.
// ---------------------------------------------------------------------------
console.log('\n=== Agent Goals：objective 驱动直到判定完成 ===\n');

const goalThread = 'demo-goal-thread';
await agent.setObjective(
  '产出一份莫干山一日游完整方案，必须同时包含：具体交通选项、总预算金额、装备清单。',
  { threadId: goalThread, resourceId: 'demo-advanced-user' },
);

const stream = await agent.stream('帮我规划莫干山一日游，从上海出发，就我一个人。', {
  memory: { thread: goalThread, resource: 'demo-advanced-user' },
  maxSteps: 15,
});

for await (const chunk of stream.fullStream as AsyncIterable<{
  type: string;
  payload?: Record<string, unknown>;
}>) {
  if (chunk.type === 'tool-call') {
    console.log(`→ 分派 ${String(chunk.payload?.toolName ?? '')}`);
  } else if (chunk.type === 'text-delta') {
    process.stdout.write(String(chunk.payload?.text ?? ''));
  }
}
console.log('\n');

const objective = await agent.getObjective({ threadId: goalThread });
console.log('objective 判定记录:', JSON.stringify(objective, null, 2));
