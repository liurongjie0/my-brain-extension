import { Agent } from '@mastra/core/agent';
import { supportMemory } from '../advanced/memory.ts';
import {
  supportInputProcessors,
  supportOutputProcessors,
} from '../advanced/processors.ts';
import { supportReplyQualityScorer } from '../advanced/scorers.ts';
import { supportAgentRefundTools } from '../tools/refund-tools.ts';

export const defaultSupportAgentModel = 'deepseek/deepseek-chat';

export function resolveSupportAgentModel(
  env: NodeJS.ProcessEnv = process.env,
): string {
  return env.SUPPORT_AGENT_MODEL ?? defaultSupportAgentModel;
}

export const supportAgentModel = resolveSupportAgentModel();

export const supportAgent = new Agent({
  id: 'support-agent',
  name: 'Support Agent',
  description: 'Customer-support agent for mock refund requests.',
  instructions: `
You are a careful customer-support agent for refund requests.

When a customer asks for a refund:
- Ask for the order ID if the customer has not provided one.
- Handle the request step by step with the tools, in this order:
  1. lookupOrderTool to fetch the order.
  2. assessRefundPolicyTool to check refund eligibility.
  3. scoreRefundRiskTool to score the refund risk.
  4. decideRefundApprovalTool to decide the approval path.
  5. issueMockRefundTool only when the decision status is auto-approved.
  6. draftRefundReplyTool to draft the customer-facing reply.
- Pass each tool the exact objects returned by the previous tools.
- If the decision status is needs-approval, do NOT issue the refund; explain that a human review is required.
- Never claim that a refund happened unless issueMockRefundTool returned a succeeded refund.
- Include a concise "处理过程" summary of the steps you took.
- Keep customer replies concise, calm, and specific.
- Prefer Chinese when the user writes Chinese or requestContext.locale is zh-CN.
`,
  model: supportAgentModel,
  // Studio's editor can still override these instructions: stored versions
  // saved in the UI take precedence, and the code value is the fallback.
  // (`editor: { instructions: true }` would hand ownership fully to Studio
  // and forbid code-defined instructions.)
  // The granular tool chain takes up to 6 tool steps plus the final reply,
  // above the default stepCountIs(5) loop budget.
  defaultOptions: { maxSteps: 12 },
  memory: supportMemory,
  inputProcessors: supportInputProcessors,
  outputProcessors: supportOutputProcessors,
  maxProcessorRetries: 1,
  tools: supportAgentRefundTools,
  // Score every live reply so the Studio evaluation pages have data;
  // lower the rate if this were real traffic.
  scorers: {
    supportReplyQualityScorer: {
      scorer: supportReplyQualityScorer,
      sampling: { type: 'ratio', rate: 1 },
    },
  },
});
