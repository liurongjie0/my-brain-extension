import { getMockOrder, type Order } from './orders.ts';

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

export interface RefundProcessStep {
  id:
    | 'lookup-order'
    | 'assess-policy'
    | 'score-risk'
    | 'decide-approval'
    | 'issue-refund'
    | 'wait-human-approval'
    | 'draft-reply';
  label: string;
  summary: string;
}

export interface RefundProcessResult {
  status: RefundApprovalDecision['status'];
  order: Order;
  policy: RefundPolicyAssessment;
  risk: RefundRiskAssessment;
  decision: RefundApprovalDecision;
  refund?: MockRefund;
  customerReply: string;
  steps: RefundProcessStep[];
}

export type RefundReplyLocale = 'zh-CN' | 'en-US';

export interface RefundReplyInput {
  order: Pick<Order, 'customerName' | 'itemName'>;
  policy: RefundPolicyAssessment;
  decision: RefundApprovalDecision;
  refund?: MockRefund;
  humanDeclined?: boolean;
}

export interface RefundProcessOptions extends RefundPolicyOptions {
  approval?: HumanApproval;
  locale?: RefundReplyLocale;
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

function formatRefundAmount(cents: number): string {
  return `$${(cents / 100).toFixed(2)}`;
}

export function draftRefundReply(
  input: RefundReplyInput,
  locale: RefundReplyLocale = 'en-US',
): string {
  const { order, policy, decision, refund, humanDeclined } = input;
  const amount = formatRefundAmount(refund?.amountCents ?? policy.refundableCents);

  if (locale === 'zh-CN') {
    if (!policy.eligible || decision.status === 'rejected') {
      return `您好 ${order.customerName}，我已查看您的 ${order.itemName} 订单，目前不能退款，原因是：${policy.reason}`;
    }

    if (humanDeclined) {
      return `您好 ${order.customerName}，您的 ${order.itemName} 退款申请已经过人工审核，很抱歉暂时无法批准。`;
    }

    if (decision.status === 'needs-approval' && !refund) {
      return `您好 ${order.customerName}，您的 ${order.itemName} 退款申请符合基础政策，但需要人工审批后再处理。`;
    }

    return `您好 ${order.customerName}，您的 ${order.itemName} 退款已处理，金额为 ${amount}。`;
  }

  if (!policy.eligible || decision.status === 'rejected') {
    return `Hi ${order.customerName}, I checked your ${order.itemName} order and cannot issue a refund because ${policy.reason}`;
  }

  if (humanDeclined) {
    return `Hi ${order.customerName}, your refund request for ${order.itemName} was reviewed, but we cannot approve it right now.`;
  }

  if (decision.status === 'needs-approval' && !refund) {
    return `Hi ${order.customerName}, your refund request for ${order.itemName} needs a quick human review before we process it.`;
  }

  return `Hi ${order.customerName}, your refund for ${order.itemName} has been processed for ${amount}.`;
}

export function processRefundRequest(
  orderId: string,
  options: RefundProcessOptions = {},
): RefundProcessResult {
  const locale = options.locale ?? 'en-US';
  const steps: RefundProcessStep[] = [];
  const order = getMockOrder(orderId);
  steps.push({
    id: 'lookup-order',
    label: 'Lookup order',
    summary: `Found ${order.itemName} for ${order.customerName}.`,
  });

  const policy = assessRefundPolicy(order, options);
  steps.push({
    id: 'assess-policy',
    label: 'Assess refund policy',
    summary: policy.reason,
  });

  const risk = scoreRefundRisk(order, policy);
  steps.push({
    id: 'score-risk',
    label: 'Score refund risk',
    summary: `${risk.level}: ${risk.reasons.join(', ')}`,
  });

  const decision = decideRefundApproval(order, policy, risk);
  steps.push({
    id: 'decide-approval',
    label: 'Decide approval path',
    summary: decision.reason,
  });

  let refund: MockRefund | undefined;
  if (decision.status === 'auto-approved') {
    refund = executeMockRefund(order, policy, decision);
    steps.push({
      id: 'issue-refund',
      label: 'Issue refund',
      summary: `Processed ${formatRefundAmount(refund.amountCents)} refund.`,
    });
  } else if (decision.status === 'needs-approval' && options.approval) {
    refund = executeMockRefund(order, policy, decision, options.approval);
    steps.push({
      id: 'issue-refund',
      label: 'Issue refund after approval',
      summary: `Processed ${formatRefundAmount(refund.amountCents)} refund after human approval.`,
    });
  } else if (decision.status === 'needs-approval') {
    steps.push({
      id: 'wait-human-approval',
      label: 'Wait for human approval',
      summary: 'Refund is paused until a human approves it.',
    });
  }

  const customerReply = draftRefundReply({ order, policy, decision, refund }, locale);
  steps.push({
    id: 'draft-reply',
    label: 'Draft customer reply',
    summary: customerReply,
  });

  return {
    status: decision.status,
    order,
    policy,
    risk,
    decision,
    refund,
    customerReply,
    steps,
  };
}
