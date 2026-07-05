import { describe, expect, it } from 'vitest';
import { getMockOrder } from './orders.ts';
import {
  assessRefundPolicy,
  decideRefundApproval,
  executeMockRefund,
  scoreRefundRisk,
} from './refunds.ts';

const fixedNow = new Date('2026-07-05T00:00:00.000Z');

describe('refund domain rules', () => {
  it('auto-approves a small eligible refund', () => {
    const order = getMockOrder('ord_small_recent');
    const policy = assessRefundPolicy(order, { now: fixedNow });
    const risk = scoreRefundRisk(order, policy);
    const decision = decideRefundApproval(order, policy, risk);

    expect(policy.eligible).toBe(true);
    expect(risk.level).toBe('low');
    expect(decision.status).toBe('auto-approved');
    expect(decision.requiresHumanApproval).toBe(false);
  });

  it('requires human approval for a high-value refund', () => {
    const order = getMockOrder('ord_high_value_recent');
    const policy = assessRefundPolicy(order, { now: fixedNow });
    const risk = scoreRefundRisk(order, policy);
    const decision = decideRefundApproval(order, policy, risk);

    expect(policy.eligible).toBe(true);
    expect(risk.level).toBe('high');
    expect(decision.status).toBe('needs-approval');
    expect(decision.requiresHumanApproval).toBe(true);
    expect(decision.reason).toContain('high-value');
  });

  it('executes a mock refund after approval', () => {
    const order = getMockOrder('ord_high_value_recent');
    const policy = assessRefundPolicy(order, { now: fixedNow });
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
    const policy = assessRefundPolicy(order, { now: fixedNow });
    const risk = scoreRefundRisk(order, policy);
    const decision = decideRefundApproval(order, policy, risk);

    expect(() =>
      executeMockRefund(order, policy, decision, {
        approved: false,
        note: 'Refund request conflicts with fraud review.',
      }),
    ).toThrow('Refund was declined');
  });
});
