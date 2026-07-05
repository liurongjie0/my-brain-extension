import { describe, expect, it } from 'vitest';
import { injectChineseOverlay } from './injector.ts';

describe('Studio Chinese overlay injector', () => {
  it('injects the overlay script before the closing body tag', () => {
    const html = '<html><body><main>Mastra Studio</main></body></html>';
    const script = 'console.log("zh");';

    const result = injectChineseOverlay(html, script);

    expect(result).toContain('<script type="module" data-mastra-zh-overlay>');
    expect(result).toContain(script);
    expect(result.indexOf(script)).toBeLessThan(result.indexOf('</body>'));
  });

  it('does not duplicate the overlay script', () => {
    const html = injectChineseOverlay(
      '<html><body><main>Mastra Studio</main></body></html>',
      'console.log("first");',
    );

    const result = injectChineseOverlay(html, 'console.log("second");');

    expect(result.match(/data-mastra-zh-overlay/g)).toHaveLength(1);
    expect(result).toContain('console.log("second");');
    expect(result).not.toContain('console.log("first");');
  });
});
