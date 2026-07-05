import { describe, expect, it } from 'vitest';
import { PLACEHOLDER_TRANSLATIONS, translateText } from './translations.ts';

describe('Studio Chinese translations', () => {
  it('translates common Mastra Studio navigation labels', () => {
    expect(translateText('Agents')).toBe('智能体');
    expect(translateText('Workflows')).toBe('工作流');
    expect(translateText('Tools')).toBe('工具');
    expect(translateText('Settings')).toBe('设置');
  });

  it('keeps unknown text unchanged', () => {
    expect(translateText('Custom Demo Label')).toBe('Custom Demo Label');
  });

  it('covers chat input placeholders', () => {
    expect(PLACEHOLDER_TRANSLATIONS['Enter your message...']).toBe('输入消息...');
    expect(PLACEHOLDER_TRANSLATIONS['Filter by name or instructions']).toBe(
      '按名称或指令筛选',
    );
  });
});
