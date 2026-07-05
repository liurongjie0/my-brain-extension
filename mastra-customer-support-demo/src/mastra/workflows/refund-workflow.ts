import { createStep, createWorkflow } from '@mastra/core/workflows';
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

const workflowInputSchema = z.object({
  orderId: z.string(),
  customerMessage: z.string(),
  nowIso: z.string().optional(),
});

const lookupOutputSchema = workflowInputSchema.extend({
  order: orderSchema,
});

const assessmentOutputSchema = lookupOutputSchema.extend({
  policy: policySchema,
  risk: riskSchema,
  decision: decisionSchema,
});

const approvalOutputSchema = assessmentOutputSchema.extend({
  approvalStatus: z.enum(['not-required', 'approved', 'declined', 'rejected']),
  approval: approvalSchema.optional(),
});

const executionOutputSchema = approvalOutputSchema.extend({
  refund: refundSchema.optional(),
});

const workflowOutputSchema = z.object({
  status: z.enum(['refunded', 'declined', 'rejected']),
  orderId: z.string(),
  reply: z.string(),
  policy: policySchema,
  risk: riskSchema,
  decision: decisionSchema,
  refund: refundSchema.optional(),
  approval: approvalSchema.optional(),
});

export const lookupRefundOrderStep = createStep({
  id: 'lookup-refund-order',
  inputSchema: workflowInputSchema,
  outputSchema: lookupOutputSchema,
  execute: async ({ inputData }) => ({
    ...inputData,
    order: getMockOrder(inputData.orderId),
  }),
});

export const assessRefundRequestStep = createStep({
  id: 'assess-refund-request',
  inputSchema: lookupOutputSchema,
  outputSchema: assessmentOutputSchema,
  execute: async ({ inputData }) => {
    const policy = assessRefundPolicy(
      inputData.order,
      inputData.nowIso ? { now: new Date(inputData.nowIso) } : {},
    );
    const risk = scoreRefundRisk(inputData.order, policy);
    const decision = decideRefundApproval(inputData.order, policy, risk);

    return {
      ...inputData,
      policy,
      risk,
      decision,
    };
  },
});

export const approveRefundStep = createStep({
  id: 'approve-refund',
  inputSchema: assessmentOutputSchema,
  outputSchema: approvalOutputSchema,
  resumeSchema: approvalSchema,
  suspendSchema: z.object({
    reason: z.string(),
    orderId: z.string(),
    customerName: z.string(),
    itemName: z.string(),
    amountCents: z.number().int().nonnegative(),
    riskReasons: z.array(z.string()),
    customerMessage: z.string(),
  }),
  execute: async ({ inputData, resumeData, suspend }) => {
    if (inputData.decision.status === 'rejected') {
      return {
        ...inputData,
        approvalStatus: 'rejected' as const,
      };
    }

    if (!inputData.decision.requiresHumanApproval) {
      return {
        ...inputData,
        approvalStatus: 'not-required' as const,
      };
    }

    if (!resumeData) {
      return await suspend(
        {
          reason: inputData.decision.reason,
          orderId: inputData.order.id,
          customerName: inputData.order.customerName,
          itemName: inputData.order.itemName,
          amountCents: inputData.policy.refundableCents,
          riskReasons: inputData.risk.reasons,
          customerMessage: inputData.customerMessage,
        },
        { resumeLabel: 'approve-refund' },
      );
    }

    return {
      ...inputData,
      approvalStatus: resumeData.approved ? ('approved' as const) : ('declined' as const),
      approval: resumeData,
    };
  },
});

export const issueRefundStep = createStep({
  id: 'issue-refund',
  inputSchema: approvalOutputSchema,
  outputSchema: executionOutputSchema,
  execute: async ({ inputData }) => {
    if (inputData.approvalStatus === 'rejected' || inputData.approvalStatus === 'declined') {
      return inputData;
    }

    return {
      ...inputData,
      refund: executeMockRefund(
        inputData.order,
        inputData.policy,
        inputData.decision,
        inputData.approval,
      ),
    };
  },
});

export const draftRefundReplyStep = createStep({
  id: 'draft-refund-reply',
  inputSchema: executionOutputSchema,
  outputSchema: workflowOutputSchema,
  execute: async ({ inputData }) => {
    if (inputData.approvalStatus === 'rejected') {
      return {
        status: 'rejected' as const,
        orderId: inputData.order.id,
        reply: `Hi ${inputData.order.customerName}, I checked your ${inputData.order.itemName} order and cannot issue a refund because ${inputData.policy.reason}`,
        policy: inputData.policy,
        risk: inputData.risk,
        decision: inputData.decision,
        approval: inputData.approval,
      };
    }

    if (inputData.approvalStatus === 'declined') {
      return {
        status: 'declined' as const,
        orderId: inputData.order.id,
        reply: `Hi ${inputData.order.customerName}, your refund request for ${inputData.order.itemName} was reviewed, but we cannot approve it right now.`,
        policy: inputData.policy,
        risk: inputData.risk,
        decision: inputData.decision,
        approval: inputData.approval,
      };
    }

    const refund = inputData.refund;
    if (!refund) {
      throw new Error('Refund step did not produce a refund.');
    }

    return {
      status: 'refunded' as const,
      orderId: inputData.order.id,
      reply: `Hi ${inputData.order.customerName}, your refund for ${inputData.order.itemName} has been approved and processed for $${(refund.amountCents / 100).toFixed(2)}.`,
      policy: inputData.policy,
      risk: inputData.risk,
      decision: inputData.decision,
      refund,
      approval: inputData.approval,
    };
  },
});

export const refundWorkflow = createWorkflow({
  id: 'refund-workflow',
  inputSchema: workflowInputSchema,
  outputSchema: workflowOutputSchema,
})
  .then(lookupRefundOrderStep)
  .then(assessRefundRequestStep)
  .then(approveRefundStep)
  .then(issueRefundStep)
  .then(draftRefundReplyStep)
  .commit();
