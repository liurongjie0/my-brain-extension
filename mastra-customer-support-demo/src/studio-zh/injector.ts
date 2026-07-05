const overlayPattern =
  /\s*<script type="module" data-mastra-zh-overlay>[\s\S]*?<\/script>/;

export function injectChineseOverlay(html: string, script: string): string {
  const tag = `<script type="module" data-mastra-zh-overlay>\n${script}\n</script>`;
  const cleanedHtml = html.replace(overlayPattern, '');

  if (cleanedHtml.includes('</body>')) {
    return cleanedHtml.replace('</body>', `${tag}\n</body>`);
  }

  return `${cleanedHtml}\n${tag}`;
}
