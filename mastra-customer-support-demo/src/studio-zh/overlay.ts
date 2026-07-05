import {
  PLACEHOLDER_TRANSLATIONS,
  TEXT_TRANSLATIONS,
  TITLE_TRANSLATIONS,
} from './translations.ts';
import {
  shouldSkipTextNodeParent,
  shouldSkipTranslationSubtree,
} from './dom-filter.ts';
import {
  translateNodeValue,
  type TextTranslationState,
} from './node-translator.ts';

const translatedNodes = new WeakMap<Text, TextTranslationState>();
const pendingRoots = new Set<ParentNode>();
const pendingTextNodes = new Set<Text>();
let flushScheduled = false;

function translateTextNode(node: Text): void {
  const currentValue = node.nodeValue ?? '';
  const state = translateNodeValue(currentValue, translatedNodes.get(node));

  if (state.translated !== currentValue) {
    node.nodeValue = state.translated;
  }

  translatedNodes.set(node, state);
}

function translateElementAttributes(element: Element): void {
  if (shouldSkipTranslationSubtree(element)) {
    return;
  }

  if (element instanceof HTMLInputElement || element instanceof HTMLTextAreaElement) {
    const translatedPlaceholder = PLACEHOLDER_TRANSLATIONS[element.placeholder];
    if (translatedPlaceholder) {
      element.placeholder = translatedPlaceholder;
    }
  }

  const title = element.getAttribute('title');
  if (title && TITLE_TRANSLATIONS[title]) {
    element.setAttribute('title', TITLE_TRANSLATIONS[title]);
  }

  const ariaLabel = element.getAttribute('aria-label');
  if (ariaLabel && (TEXT_TRANSLATIONS[ariaLabel] || TITLE_TRANSLATIONS[ariaLabel])) {
    element.setAttribute('aria-label', TEXT_TRANSLATIONS[ariaLabel] ?? TITLE_TRANSLATIONS[ariaLabel]);
  }
}

function walk(root: ParentNode): void {
  if (root instanceof Element && shouldSkipTranslationSubtree(root)) {
    return;
  }

  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  const nodes: Text[] = [];

  while (walker.nextNode()) {
    nodes.push(walker.currentNode as Text);
  }

  for (const node of nodes) {
    const parent = node.parentElement;
    if (!parent || shouldSkipTextNodeParent(parent)) {
      continue;
    }

    translateTextNode(node);
  }

  const elements = root instanceof Element ? [root, ...root.querySelectorAll('*')] : root.querySelectorAll('*');
  for (const element of elements) {
    translateElementAttributes(element);
  }
}

function scheduleFlush(): void {
  if (flushScheduled) {
    return;
  }

  flushScheduled = true;

  const flush = () => {
    flushScheduled = false;

    const roots = [...pendingRoots];
    const textNodes = [...pendingTextNodes];
    pendingRoots.clear();
    pendingTextNodes.clear();

    for (const root of roots) {
      walk(root);
    }

    for (const node of textNodes) {
      const parent = node.parentElement;
      if (parent && !shouldSkipTextNodeParent(parent)) {
        translateTextNode(node);
      }
    }
  };

  if (typeof requestAnimationFrame === 'function') {
    requestAnimationFrame(flush);
  } else {
    setTimeout(flush, 0);
  }
}

function scheduleWalk(root: ParentNode): void {
  pendingRoots.add(root);
  scheduleFlush();
}

function scheduleTextNode(node: Text): void {
  pendingTextNodes.add(node);
  scheduleFlush();
}

function installChineseOverlay(): void {
  walk(document.body);

  const observer = new MutationObserver((mutations) => {
    for (const mutation of mutations) {
      for (const node of mutation.addedNodes) {
        if (node instanceof Element || node instanceof DocumentFragment) {
          scheduleWalk(node);
        } else if (node instanceof Text) {
          scheduleTextNode(node);
        }
      }

      if (mutation.type === 'characterData' && mutation.target instanceof Text) {
        scheduleTextNode(mutation.target);
      }
    }
  });

  observer.observe(document.body, {
    childList: true,
    characterData: true,
    subtree: true,
  });
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', installChineseOverlay, { once: true });
} else {
  installChineseOverlay();
}
