import {
  PLACEHOLDER_TRANSLATIONS,
  TEXT_TRANSLATIONS,
  TITLE_TRANSLATIONS,
  translateText,
} from './translations.ts';

const translatedNodes = new WeakMap<Node, string>();

function translateTextNode(node: Text): void {
  const original = translatedNodes.get(node) ?? node.nodeValue ?? '';
  const translated = translateText(original);

  if (translated !== node.nodeValue) {
    node.nodeValue = original.replace(original.trim(), translated);
  }

  translatedNodes.set(node, original);
}

function translateElementAttributes(element: Element): void {
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
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  const nodes: Text[] = [];

  while (walker.nextNode()) {
    nodes.push(walker.currentNode as Text);
  }

  for (const node of nodes) {
    const parent = node.parentElement;
    if (!parent || ['SCRIPT', 'STYLE', 'TEXTAREA'].includes(parent.tagName)) {
      continue;
    }

    translateTextNode(node);
  }

  const elements = root instanceof Element ? [root, ...root.querySelectorAll('*')] : root.querySelectorAll('*');
  for (const element of elements) {
    translateElementAttributes(element);
  }
}

function installChineseOverlay(): void {
  walk(document.body);

  const observer = new MutationObserver((mutations) => {
    for (const mutation of mutations) {
      for (const node of mutation.addedNodes) {
        if (node instanceof Element || node instanceof DocumentFragment) {
          walk(node);
        } else if (node instanceof Text) {
          translateTextNode(node);
        }
      }

      if (mutation.type === 'characterData' && mutation.target instanceof Text) {
        translateTextNode(mutation.target);
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
