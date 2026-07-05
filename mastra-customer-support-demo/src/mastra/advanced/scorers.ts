import { createScorer } from '@mastra/core/evals';

function stringifyScorerValue(value: unknown): string {
  if (typeof value === 'string') {
    return value;
  }

  return JSON.stringify(value);
}

export const supportReplyQualityScorer = createScorer({
  id: 'support-reply-quality',
  name: 'Support Reply Quality',
  description:
    'Scores whether a customer-support reply is concrete, policy-aware, and avoids unsafe refund promises.',
})
  .generateScore(({ run }) => {
    const text = stringifyScorerValue(run.output);
    const lowerText = text.toLowerCase();
    let score = 0;

    if (/退款|refund|订单|order|ord_/.test(lowerText)) {
      score += 0.35;
    }

    if (/已处理|processed|人工|审批|review|policy|政策/.test(lowerText)) {
      score += 0.35;
    }

    if (!/保证|一定|guarantee|立刻全部/.test(lowerText)) {
      score += 0.3;
    }

    return Number(Math.min(score, 1).toFixed(2));
  })
  .generateReason(({ score }) => {
    if (score >= 0.75) {
      return '回复具体，提到了订单或退款状态，并且没有做不安全承诺。';
    }

    return '缺少订单、政策或处理状态等具体信息，或包含过度承诺。';
  });
