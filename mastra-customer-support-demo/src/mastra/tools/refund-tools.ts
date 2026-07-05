import { createTool } from '@mastra/core/tools';
import { z } from 'zod';
import { getMockOrder } from '../../domain/orders.ts';
import {
  assessRefundPolicy,
  decideRefundApproval,
  executeMockRefund,
  scoreRefundRisk,
} from '../../domain/refunds.ts';

const orderSchema = z.object({
  id: z.string(),
  customerId: z.string(),
  customerName: z.string(),
  itemName: z.string(),
  status: z.enum(['delivered', 'processing', 'cancelled', 'refunded']),
  totalCents: z.number().int().nonnegative(),
  currency: z.literal('USD'),
  deliveredAt: z.string().nullable(),
  refundHistoryCount: z.number().int().nonnegative(),
});

const policySchema = z.object({
  eligible: z.boolean(),
  reason: z.string(),
  refundableCents: z.number().int().nonnegative(),
  daysSinceDelivery: z.number().int().nullable(),
});

const riskSchema = z.object({
  level: z.enum(['low', 'medium', 'high']),
  reasons: z.array(z.string()),
});

const decisionSchema = z.object({
  status: z.enum(['auto-approved', 'needs-approval', 'rejected']),
  requiresHumanApproval: z.boolean(),
  reason: z.string(),
});

const approvalSchema = z.object({
  approved: z.boolean(),
  note: z.string().optional(),
});

const refundSchema = z.object({
  providerRefundId: z.string(),
  orderId: z.string(),
  amountCents: z.number().int().nonnegative(),
  currency: z.literal('USD'),
  status: z.literal('succeeded'),
  approvalNote: z.string().optional(),
});

export const lookupOrderTool = createTool({
  id: 'lookup-order',
  description: 'Look up a mock customer order by order ID.',
  inputSchema: z.object({
    orderId: z.string().describe('Mock order ID, such as ord_small_recent.'),
  }),
  outputSchema: z.object({
    order: orderSchema,
  }),
  execute: async ({ orderId }) => ({
    order: getMockOrder(orderId),
  }),
});

export const assessRefundPolicyTool = createTool({
  id: 'assess-refund-policy',
  description: 'Check whether a delivered order is eligible for a refund.',
  inputSchema: z.object({
    order: orderSchema,
    nowIso: z.string().optional(),
  }),
  outputSchema: z.object({
    policy: policySchema,
  }),
  execute: async ({ order, nowIso }) => ({
    policy: assessRefundPolicy(order, nowIso ? { now: new Date(nowIso) } : {}),
  }),
});

export const scoreRefundRiskTool = createTool({
  id: 'score-refund-risk',
  description: 'Score refund risk from order value, eligibility, and history.',
  inputSchema: z.object({
    order: orderSchema,
    policy: policySchema,
  }),
  outputSchema: z.object({
    risk: riskSchema,
  }),
  execute: async ({ order, policy }) => ({
    risk: scoreRefundRisk(order, policy),
  }),
});

export const decideRefundApprovalTool = createTool({
  id: 'decide-refund-approval',
  description: 'Decide whether a refund can be automatic or needs human approval.',
  inputSchema: z.object({
    order: orderSchema,
    policy: policySchema,
    risk: riskSchema,
  }),
  outputSchema: z.object({
    decision: decisionSchema,
  }),
  execute: async ({ order, policy, risk }) => ({
    decision: decideRefundApproval(order, policy, risk),
  }),
});

export const issueMockRefundTool = createTool({
  id: 'issue-mock-refund',
  description: 'Execute a mock refund after automatic approval or human approval.',
  inputSchema: z.object({
    order: orderSchema,
    policy: policySchema,
    decision: decisionSchema,
    approval: approvalSchema.optional(),
  }),
  outputSchema: z.object({
    refund: refundSchema,
  }),
  execute: async ({ order, policy, decision, approval }) => ({
    refund: executeMockRefund(order, policy, decision, approval),
  }),
});

export const draftRefundReplyTool = createTool({
  id: 'draft-refund-reply',
  description: 'Draft a short customer-facing refund response.',
  inputSchema: z.object({
    order: orderSchema,
    policy: policySchema,
    decision: decisionSchema,
    refund: refundSchema.optional(),
  }),
  outputSchema: z.object({
    reply: z.string(),
  }),
  execute: async ({ order, policy, decision, refund }) => {
    if (!policy.eligible || decision.status === 'rejected') {
      return {
        reply: `Hi ${order.customerName}, I checked your ${order.itemName} order and cannot issue a refund because ${policy.reason}`,
      };
    }

    if (decision.status === 'needs-approval' && !refund) {
      return {
        reply: `Hi ${order.customerName}, your refund request for ${order.itemName} needs a quick human review before we process it.`,
      };
    }

    const amount = refund ? `$${(refund.amountCents / 100).toFixed(2)}` : 'the full amount';
    return {
      reply: `Hi ${order.customerName}, your refund for ${order.itemName} has been approved and processed for ${amount}.`,
    };
  },
});

export const refundTools = {
  lookupOrderTool,
  assessRefundPolicyTool,
  scoreRefundRiskTool,
  decideRefundApprovalTool,
  issueMockRefundTool,
  draftRefundReplyTool,
};
