import { createTool } from '@mastra/core/tools';
import { z } from 'zod';
import { getMockOrder } from '../../domain/orders.ts';
import {
  assessRefundPolicy,
  decideRefundApproval,
  draftRefundReply,
  executeMockRefund,
  processRefundRequest,
  scoreRefundRisk,
} from '../../domain/refunds.ts';
import {
  approvalSchema,
  decisionSchema,
  localeSchema,
  orderSchema,
  policySchema,
  refundProcessSchema,
  refundSchema,
  riskSchema,
} from '../schemas.ts';

export const handleRefundRequestInputSchema = z.object({
  orderId: z.string().describe('Mock order ID, such as ord_small_recent.'),
  locale: localeSchema.default('zh-CN'),
});

export const handleRefundRequestTool = createTool({
  id: 'handle-refund-request',
  description:
    'Handle a refund request end-to-end using an order ID and locale, then return traceable support steps.',
  inputSchema: handleRefundRequestInputSchema,
  outputSchema: z.object({
    process: refundProcessSchema,
  }),
  execute: async ({ orderId, locale }) => ({
    process: processRefundRequest(orderId, {
      locale,
    }),
  }),
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
    locale: localeSchema.default('en-US'),
  }),
  outputSchema: z.object({
    reply: z.string(),
  }),
  execute: async ({ order, policy, decision, refund, locale }) => ({
    reply: draftRefundReply({ order, policy, decision, refund }, locale),
  }),
});

export const refundTools = {
  handleRefundRequestTool,
  lookupOrderTool,
  assessRefundPolicyTool,
  scoreRefundRiskTool,
  decideRefundApprovalTool,
  issueMockRefundTool,
  draftRefundReplyTool,
};

// The chat agent gets the granular tools so each decision step is a visible
// tool call; the one-shot handleRefundRequestTool stays available for
// programmatic use and the Studio tools page.
export const supportAgentRefundTools = {
  lookupOrderTool,
  assessRefundPolicyTool,
  scoreRefundRiskTool,
  decideRefundApprovalTool,
  issueMockRefundTool,
  draftRefundReplyTool,
};
