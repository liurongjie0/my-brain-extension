import { describe, expect, it } from 'vitest';
import {
  handleRefundRequestInputSchema,
  handleRefundRequestTool,
  supportAgentRefundTools,
} from './refund-tools.ts';

describe('supportAgentRefundTools', () => {
  it('gives the chat agent the granular tools instead of the one-shot tool', () => {
    expect(Object.keys(supportAgentRefundTools)).toEqual([
      'lookupOrderTool',
      'assessRefundPolicyTool',
      'scoreRefundRiskTool',
      'decideRefundApprovalTool',
      'issueMockRefundTool',
      'draftRefundReplyTool',
    ]);
  });
});

describe('handleRefundRequestTool', () => {
  it('keeps model-facing input minimal for streaming tool calls', () => {
    expect(Object.keys(handleRefundRequestInputSchema.shape)).toEqual([
      'orderId',
      'locale',
    ]);
  });

  it('returns a complete traceable refund process for a low-risk order', async () => {
    const execute = handleRefundRequestTool.execute;
    expect(execute).toBeDefined();

    if (!execute) {
      throw new Error('handleRefundRequestTool execute is not configured');
    }

    const context = {} as Parameters<typeof execute>[1];
    const result = await execute({
      orderId: 'ord_small_recent',
      locale: 'zh-CN',
    }, context);

    if (!result || !('process' in result)) {
      throw new Error('handleRefundRequestTool did not return a result');
    }

    expect(result.process.status).toBe('auto-approved');
    expect(result.process.refund?.status).toBe('succeeded');
    expect(result.process.steps.map((step) => step.id)).toContain(
      'issue-refund',
    );
    expect(result.process.customerReply).toContain('退款已处理');
  });
});
