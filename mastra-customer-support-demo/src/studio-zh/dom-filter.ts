export type TranslationElementLike = {
  tagName?: string;
  classList?: {
    contains(className: string): boolean;
  };
  getAttribute?(name: string): string | null;
  parentElement?: TranslationElementLike | null;
};

const SKIPPED_SUBTREE_TAGS = new Set(['CODE', 'KBD', 'PRE', 'SAMP']);

const SKIPPED_SUBTREE_CLASSES = [
  'cm-content',
  'cm-editor',
  'cm-line',
  'cm-scroller',
  'monaco-editor',
];

export function shouldSkipTranslationSubtree(
  element: TranslationElementLike,
): boolean {
  const tagName = element.tagName?.toUpperCase();
  if (tagName && SKIPPED_SUBTREE_TAGS.has(tagName)) {
    return true;
  }

  if (element.getAttribute?.('contenteditable') === 'true') {
    return true;
  }

  if (
    element.getAttribute?.('role') === 'textbox' &&
    element.getAttribute?.('aria-multiline') === 'true'
  ) {
    return true;
  }

  if (
    SKIPPED_SUBTREE_CLASSES.some((className) =>
      element.classList?.contains(className),
    )
  ) {
    return true;
  }

  return element.parentElement
    ? shouldSkipTranslationSubtree(element.parentElement)
    : false;
}

export function shouldSkipTextNodeParent(
  parent: TranslationElementLike,
): boolean {
  const tagName = parent.tagName?.toUpperCase();
  if (tagName && ['SCRIPT', 'STYLE', 'TEXTAREA'].includes(tagName)) {
    return true;
  }

  return shouldSkipTranslationSubtree(parent);
}
