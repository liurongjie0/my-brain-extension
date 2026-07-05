import { mastra } from '../src/mastra/index.ts';
import { approveRefundStep } from '../src/mastra/workflows/refund-workflow.ts';

const workflow = mastra.getWorkflow('refundWorkflow');
const run = await workflow.createRun();

const initialResult = await run.start({
  inputData: {
    orderId: 'ord_high_value_recent',
    customerMessage: 'The workstation bundle was delivered damaged. Please refund the full order.',
  },
  outputOptions: {
    includeResumeLabels: true,
  },
});

console.log('--- initial result ---');
console.log(
  JSON.stringify(
    {
      status: initialResult.status,
      stepExecutionPath: initialResult.stepExecutionPath,
      suspendPayload: initialResult.status === 'suspended' ? initialResult.suspendPayload : undefined,
      resumeLabels: initialResult.resumeLabels,
    },
    null,
    2,
  ),
);

if (initialResult.status !== 'suspended') {
  throw new Error(`Expected workflow to suspend, got ${initialResult.status}`);
}

const resumedResult = await run.resume({
  step: approveRefundStep,
  resumeData: {
    approved: true,
    note: 'Approved after reviewing delivery damage evidence.',
  },
  outputOptions: {
    includeResumeLabels: true,
  },
});

console.log('--- resumed result ---');
console.log(
  JSON.stringify(
    {
      status: resumedResult.status,
      stepExecutionPath: resumedResult.stepExecutionPath,
      result: resumedResult.status === 'success' ? resumedResult.result : resumedResult,
    },
    null,
    2,
  ),
);
