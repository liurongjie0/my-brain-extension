import { describe, expect, it } from 'vitest';
import { getMockOrder } from './orders.ts';
import {
  assessRefundPolicy,
  decideRefundApproval,
  executeMockRefund,
  processRefundRequest,
  scoreRefundRisk,
} from './refunds.ts';

describe('refund domain rules', () => {
  it('auto-approves a small eligible refund', () => {
    const order = getMockOrder('ord_small_recent');
    const policy = assessRefundPolicy(order);
    const risk = scoreRefundRisk(order, policy);
    const decision = decideRefundApproval(order, policy, risk);

    expect(policy.eligible).toBe(true);
    expect(risk.level).toBe('low');
    expect(decision.status).toBe('auto-approved');
    expect(decision.requiresHumanApproval).toBe(false);
  });

  it('requires human approval for a high-value refund', () => {
    const order = getMockOrder('ord_high_value_recent');
    const policy = assessRefundPolicy(order);
    const risk = scoreRefundRisk(order, policy);
    const decision = decideRefundApproval(order, policy, risk);

    expect(policy.eligible).toBe(true);
    expect(risk.level).toBe('high');
    expect(decision.status).toBe('needs-approval');
    expect(decision.requiresHumanApproval).toBe(true);
    expect(decision.reason).toContain('high-value');
  });

  it('rejects refunds outside the refund window', () => {
    const order = getMockOrder('ord_old_delivery');
    const policy = assessRefundPolicy(order);
    const risk = scoreRefundRisk(order, policy);
    const decision = decideRefundApproval(order, policy, risk);

    expect(policy.eligible).toBe(false);
    expect(policy.reason).toContain('Refund window expired');
    expect(decision.status).toBe('rejected');
  });

  it('executes a mock refund after approval', () => {
    const order = getMockOrder('ord_high_value_recent');
    const policy = assessRefundPolicy(order);
    const risk = scoreRefundRisk(order, policy);
    const decision = decideRefundApproval(order, policy, risk);

    const refund = executeMockRefund(order, policy, decision, {
      approved: true,
      note: 'Customer has a valid delivery complaint.',
    });

    expect(refund.status).toBe('succeeded');
    expect(refund.amountCents).toBe(policy.refundableCents);
    expect(refund.providerRefundId).toMatch(/^mock_refund_/);
    expect(refund.approvalNote).toBe('Customer has a valid delivery complaint.');
  });

  it('does not execute a refund when approval is declined', () => {
    const order = getMockOrder('ord_high_value_recent');
    const policy = assessRefundPolicy(order);
    const risk = scoreRefundRisk(order, policy);
    const decision = decideRefundApproval(order, policy, risk);

    expect(() =>
      executeMockRefund(order, policy, decision, {
        approved: false,
        note: 'Refund request conflicts with fraud review.',
      }),
    ).toThrow('Refund was declined');
  });

  it('processes a low-risk refund as one traceable support operation', () => {
    const result = processRefundRequest('ord_small_recent', {
      locale: 'zh-CN',
    });

    expect(result.status).toBe('auto-approved');
    expect(result.refund?.status).toBe('succeeded');
    expect(result.steps.map((step) => step.id)).toEqual([
      'lookup-order',
      'assess-policy',
      'score-risk',
      'decide-approval',
      'issue-refund',
      'draft-reply',
    ]);
    expect(result.customerReply).toContain('退款已处理');
  });
});
