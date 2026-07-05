import { Memory } from '@mastra/memory';

export const supportWorkingMemoryTemplate = `# 客户支持记忆
- 客户偏好:
- 最近订单:
- 风险备注:
- 人工审批备注:`;

export const supportMemoryOptions = {
  id: 'support-memory',
  options: {
    lastMessages: 20,
    semanticRecall: false,
    generateTitle: true,
    workingMemory: {
      enabled: true,
      scope: 'resource',
      template: supportWorkingMemoryTemplate,
    },
  },
} as const;

export const supportMemory = new Memory(supportMemoryOptions);
