import type { Order } from './orders.ts';

export interface RefundPolicyOptions {
  now?: Date;
}

export interface RefundPolicyAssessment {
  eligible: boolean;
  reason: string;
  refundableCents: number;
  daysSinceDelivery: number | null;
}

export interface RefundRiskAssessment {
  level: 'low' | 'medium' | 'high';
  reasons: string[];
}

export interface RefundApprovalDecision {
  status: 'auto-approved' | 'needs-approval' | 'rejected';
  requiresHumanApproval: boolean;
  reason: string;
}

export interface HumanApproval {
  approved: boolean;
  note?: string;
}

export interface MockRefund {
  providerRefundId: string;
  orderId: string;
  amountCents: number;
  currency: Order['currency'];
  status: 'succeeded';
  approvalNote?: string;
}

const refundWindowDays = 30;
const highValueRefundCents = 50000;
const mediumValueRefundCents = 10000;

export function assessRefundPolicy(
  order: Order,
  options: RefundPolicyOptions = {},
): RefundPolicyAssessment {
  if (order.status === 'refunded') {
    return {
      eligible: false,
      reason: 'Order has already been refunded.',
      refundableCents: 0,
      daysSinceDelivery: null,
    };
  }

  if (order.status !== 'delivered' || !order.deliveredAt) {
    return {
      eligible: false,
      reason: 'Only delivered orders are eligible for self-service refunds.',
      refundableCents: 0,
      daysSinceDelivery: null,
    };
  }

  const now = options.now ?? new Date();
  const deliveredAt = new Date(order.deliveredAt);
  const daysSinceDelivery = Math.floor(
    (now.getTime() - deliveredAt.getTime()) / 86_400_000,
  );

  if (daysSinceDelivery > refundWindowDays) {
    return {
      eligible: false,
      reason: `Refund window expired after ${refundWindowDays} days.`,
      refundableCents: 0,
      daysSinceDelivery,
    };
  }

  return {
    eligible: true,
    reason: 'Order is within the refund window.',
    refundableCents: order.totalCents,
    daysSinceDelivery,
  };
}

export function scoreRefundRisk(
  order: Order,
  policy: RefundPolicyAssessment,
): RefundRiskAssessment {
  const reasons: string[] = [];

  if (!policy.eligible) {
    reasons.push('policy-ineligible');
  }

  if (order.totalCents >= highValueRefundCents) {
    reasons.push('high-value');
  } else if (order.totalCents >= mediumValueRefundCents) {
    reasons.push('medium-value');
  }

  if (order.refundHistoryCount >= 2) {
    reasons.push('repeat-refund-history');
  }

  if (reasons.includes('policy-ineligible') || reasons.includes('high-value')) {
    return { level: 'high', reasons };
  }

  if (reasons.length > 0) {
    return { level: 'medium', reasons };
  }

  return { level: 'low', reasons: ['small-recent-order'] };
}

export function decideRefundApproval(
  _order: Order,
  policy: RefundPolicyAssessment,
  risk: RefundRiskAssessment,
): RefundApprovalDecision {
  if (!policy.eligible) {
    return {
      status: 'rejected',
      requiresHumanApproval: false,
      reason: policy.reason,
    };
  }

  if (risk.level !== 'low') {
    return {
      status: 'needs-approval',
      requiresHumanApproval: true,
      reason: risk.reasons.join(', '),
    };
  }

  return {
    status: 'auto-approved',
    requiresHumanApproval: false,
    reason: 'Low-risk refund can be processed automatically.',
  };
}

export function executeMockRefund(
  order: Order,
  policy: RefundPolicyAssessment,
  decision: RefundApprovalDecision,
  approval?: HumanApproval,
): MockRefund {
  if (decision.status === 'rejected') {
    throw new Error(`Refund is not eligible: ${decision.reason}`);
  }

  if (decision.status === 'needs-approval' && !approval) {
    throw new Error('Refund requires approval before execution');
  }

  if (approval && !approval.approved) {
    throw new Error('Refund was declined');
  }

  return {
    providerRefundId: `mock_refund_${order.id}_${policy.refundableCents}`,
    orderId: order.id,
    amountCents: policy.refundableCents,
    currency: order.currency,
    status: 'succeeded',
    approvalNote: approval?.note,
  };
}
