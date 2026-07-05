import { describe, expect, it } from 'vitest';
import {
  shouldSkipTextNodeParent,
  shouldSkipTranslationSubtree,
  type TranslationElementLike,
} from './dom-filter.ts';

function fakeElement(options: {
  tagName?: string;
  classNames?: string[];
  attributes?: Record<string, string>;
  parentElement?: TranslationElementLike;
}): TranslationElementLike {
  return {
    tagName: options.tagName,
    classList: {
      contains: (className: string) =>
        options.classNames?.includes(className) ?? false,
    },
    getAttribute: (name: string) => options.attributes?.[name] ?? null,
    parentElement: options.parentElement ?? null,
  };
}

describe('Studio Chinese overlay DOM filter', () => {
  it('skips CodeMirror editor subtrees', () => {
    expect(
      shouldSkipTranslationSubtree(
        fakeElement({ tagName: 'div', classNames: ['cm-editor'] }),
      ),
    ).toBe(true);
  });

  it('skips descendants of code editor subtrees', () => {
    const editor = fakeElement({ tagName: 'div', classNames: ['cm-content'] });
    const line = fakeElement({ tagName: 'span', parentElement: editor });

    expect(shouldSkipTranslationSubtree(line)).toBe(true);
  });

  it('skips textarea text without treating textareas as skipped subtrees', () => {
    const textarea = fakeElement({ tagName: 'textarea' });

    expect(shouldSkipTextNodeParent(textarea)).toBe(true);
    expect(shouldSkipTranslationSubtree(textarea)).toBe(false);
  });
});
