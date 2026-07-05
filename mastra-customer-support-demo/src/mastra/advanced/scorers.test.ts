import { describe, expect, it } from 'vitest';
import { supportReplyQualityScorer } from './scorers.ts';

describe('supportReplyQualityScorer', () => {
  it('rewards concrete and safe refund replies', async () => {
    const result = await supportReplyQualityScorer.run({
      input: '用户询问 ord_small_recent 是否可以退款',
      output:
        '已查询订单 ord_small_recent，符合退款政策，退款已处理。金额会回到原支付方式。',
    });

    expect(result.score).toBeGreaterThanOrEqual(0.75);
    expect(result.reason).toContain('具体');
  });

  it('penalizes vague promises', async () => {
    const result = await supportReplyQualityScorer.run({
      input: '用户要求退款',
      output: '一定没问题，我保证全部立刻退给你。',
    });

    expect(result.score).toBeLessThan(0.75);
    expect(result.reason).toContain('缺少');
  });
});
