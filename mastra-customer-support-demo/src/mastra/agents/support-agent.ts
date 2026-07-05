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
