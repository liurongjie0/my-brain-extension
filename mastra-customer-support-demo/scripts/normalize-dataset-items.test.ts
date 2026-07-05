import { describe, expect, it } from 'vitest';
import { normalizeDatasetItemInput } from './normalize-dataset-items.ts';

describe('normalizeDatasetItemInput', () => {
  it('leaves plain strings alone', () => {
    expect(normalizeDatasetItemInput('ord_small_recent 退款')).toBeNull();
  });

  it('unwraps live-chat score payloads (inputMessages at top level)', () => {
    const raw = {
      inputMessages: [
        {
          id: 'msg-1',
          role: 'user',
          content: {
            format: 2,
            parts: [{ type: 'text', text: 'ord_small_recent 这个订单帮我退一下' }],
            content: 'ord_small_recent 这个订单帮我退一下',
          },
        },
      ],
      systemMessages: [{ role: 'system', content: 'instructions' }],
    };

    expect(normalizeDatasetItemInput(raw)).toEqual({
      input: 'ord_small_recent 这个订单帮我退一下',
    });
  });

  it('unwraps nested payloads ({ input: { inputMessages } })', () => {
    const raw = {
      input: {
        inputMessages: [
          {
            role: 'user',
            content: { parts: [{ type: 'text', text: '帮我退款' }] },
          },
        ],
      },
    };

    expect(normalizeDatasetItemInput(raw)).toEqual({ input: '帮我退款' });
  });

  it('unwraps experiment score payloads ({ input: string, output, groundTruth })', () => {
    const raw = {
      input: 'ord_small_recent 这个订单帮我退一下',
      output: { text: '已退款' },
      groundTruth: '应完成退款',
    };

    expect(normalizeDatasetItemInput(raw)).toEqual({
      input: 'ord_small_recent 这个订单帮我退一下',
      groundTruth: '应完成退款',
    });
  });

  it('uses the most recent user message when several exist', () => {
    const raw = {
      inputMessages: [
        { role: 'user', content: '第一句' },
        { role: 'assistant', content: '回复' },
        { role: 'user', content: '第二句' },
      ],
    };

    expect(normalizeDatasetItemInput(raw)).toEqual({ input: '第二句' });
  });
});
