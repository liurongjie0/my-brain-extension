// Multi-agent travel-planning demo: the routing agent delegates to the
// transport/lodging sub-agents, the itinerary workflow, and trail tools.
// Requires a model API key (DEEPSEEK_API_KEY by default).
import { mastra } from '../src/mastra/index.ts';

const agent = mastra.getAgent('travelPlannerAgent');

const memoryScope = {
  thread: 'demo-travel-thread',
  resource: 'demo-travel-user',
} as const;

interface StreamChunk {
  type: string;
  payload?: Record<string, unknown>;
}

function payloadString(chunk: StreamChunk, key: string): string | undefined {
  const value = chunk.payload?.[key];
  return typeof value === 'string' ? value : undefined;
}

async function runTurn(label: string, message: string): Promise<void> {
  console.log(`\n=== ${label} ===`);
  console.log(`用户: ${message}\n`);

  const stream = await agent.stream(message, {
    memory: memoryScope,
    maxSteps: 15,
  });

  for await (const chunk of stream.fullStream as AsyncIterable<StreamChunk>) {
    switch (chunk.type) {
      case 'text-delta':
        process.stdout.write(payloadString(chunk, 'text') ?? '');
        break;
      case 'tool-call': {
        const toolName = payloadString(chunk, 'toolName') ?? '(unknown tool)';
        const args = chunk.payload?.args;
        console.log(`\n→ 分派 ${toolName} ${args ? JSON.stringify(args) : ''}`);
        break;
      }
      case 'tool-result': {
        const toolName = payloadString(chunk, 'toolName') ?? '(unknown tool)';
        console.log(`← ${toolName} 返回结果`);
        break;
      }
      case 'error':
        console.error('\n[stream error]', chunk.payload);
        break;
      default:
        break;
    }
  }

  console.log('\n');
}

await runTurn(
  '第 1 轮 · 完整规划',
  '帮我规划周末两天莫干山徒步，从上海出发，2 个人，住宿预算每晚 500 以内。',
);

await runTurn(
  '第 2 轮 · 追加约束',
  '住宿预算砍到每晚 150 以内，其他不变，帮我调整方案。',
);

await runTurn(
  '第 3 轮 · 确认并出完整方案',
  '就按你推荐的来，交通选大巴。帮我出完整的逐日行程、预算汇总和装备清单（秋季）。',
);

await runTurn(
  '第 4 轮 · 触发 workspace skill',
  '我们俩都是徒步新手，这次出行要注意哪些安全事项？万一有人扭伤了怎么办？',
);
