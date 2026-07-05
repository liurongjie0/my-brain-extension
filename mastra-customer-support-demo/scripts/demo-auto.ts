import { mastra } from '../src/mastra/index.ts';

const workflow = mastra.getWorkflow('refundWorkflow');
const run = await workflow.createRun();

const result = await run.start({
  inputData: {
    orderId: 'ord_small_recent',
    customerMessage: 'The earbuds arrived with a broken charging case. Can I get a refund?',
  },
  outputOptions: {
    includeResumeLabels: true,
  },
});

console.log(
  JSON.stringify(
    {
      status: result.status,
      stepExecutionPath: result.stepExecutionPath,
      result: result.status === 'success' ? result.result : result,
    },
    null,
    2,
  ),
);
