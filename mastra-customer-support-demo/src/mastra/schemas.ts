import { z } from 'zod';

export const localeSchema = z.enum(['zh-CN', 'en-US']);

export const orderSchema = z.object({
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

export const policySchema = z.object({
  eligible: z.boolean(),
  reason: z.string(),
  refundableCents: z.number().int().nonnegative(),
  daysSinceDelivery: z.number().int().nullable(),
});

export const riskSchema = z.object({
  level: z.enum(['low', 'medium', 'high']),
  reasons: z.array(z.string()),
});

export const decisionSchema = z.object({
  status: z.enum(['auto-approved', 'needs-approval', 'rejected']),
  requiresHumanApproval: z.boolean(),
  reason: z.string(),
});

export const approvalSchema = z.object({
  approved: z.boolean(),
  note: z.string().optional(),
});

export const refundSchema = z.object({
  providerRefundId: z.string(),
  orderId: z.string(),
  amountCents: z.number().int().nonnegative(),
  currency: z.literal('USD'),
  status: z.literal('succeeded'),
  approvalNote: z.string().optional(),
});

export const processStepSchema = z.object({
  id: z.enum([
    'lookup-order',
    'assess-policy',
    'score-risk',
    'decide-approval',
    'issue-refund',
    'wait-human-approval',
    'draft-reply',
  ]),
  label: z.string(),
  summary: z.string(),
});

export const refundProcessSchema = z.object({
  status: z.enum(['auto-approved', 'needs-approval', 'rejected']),
  order: orderSchema,
  policy: policySchema,
  risk: riskSchema,
  decision: decisionSchema,
  refund: refundSchema.optional(),
  customerReply: z.string(),
  steps: z.array(processStepSchema),
});
