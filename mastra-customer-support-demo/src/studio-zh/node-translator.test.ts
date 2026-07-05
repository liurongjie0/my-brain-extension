import { describe, expect, it } from 'vitest';
import { translateNodeValue } from './node-translator.ts';

describe('Studio Chinese overlay node translator', () => {
  it('keeps the original text when revisiting an already translated node', () => {
    const firstPass = translateNodeValue('Agents');
    const secondPass = translateNodeValue(firstPass.translated, firstPass);

    expect(firstPass).toEqual({
      original: 'Agents',
      translated: '智能体',
    });
    expect(secondPass).toEqual(firstPass);
  });

  it('does not overwrite dynamic editor text with an old original value', () => {
    const previousState = translateNodeValue('{}');
    const nextPass = translateNodeValue('{"success":true}', previousState);

    expect(nextPass).toEqual({
      original: '{"success":true}',
      translated: '{"success":true}',
    });
  });
});
