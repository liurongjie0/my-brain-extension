import { spawn } from 'node:child_process';
import { copyFile, mkdir, readFile, stat, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { build } from 'esbuild';
import { injectChineseOverlay } from '../src/studio-zh/injector.ts';

const projectRoot = resolve(import.meta.dirname, '..');
const generatedHtmlPath = resolve(projectRoot, '.mastra/output/studio/index.html');
const overlaySourcePath = resolve(projectRoot, 'src/studio-zh/overlay.ts');
const overlayRuntimePath = resolve(projectRoot, '.mastra/zh-overlay.mjs');
const overlayRuntimeOutputPath = resolve(projectRoot, '.mastra/output/studio/zh-overlay.mjs');
const overlayRuntimePublicPath = '/zh-overlay.mjs';

export function buildMastraDevArgs(
  presetsPath = 'request-context-presets.json',
): string[] {
  return ['mastra', 'dev', '--request-context-presets', presetsPath];
}

async function pathExists(path: string): Promise<boolean> {
  try {
    await stat(path);
    return true;
  } catch {
    return false;
  }
}

async function buildOverlayRuntime(): Promise<void> {
  await mkdir(dirname(overlayRuntimePath), { recursive: true });
  await build({
    entryPoints: [overlaySourcePath],
    bundle: true,
    format: 'esm',
    outfile: overlayRuntimePath,
    platform: 'browser',
    sourcemap: false,
    logLevel: 'silent',
  });
}

async function injectOverlay(): Promise<void> {
  if (!(await pathExists(generatedHtmlPath))) {
    return;
  }

  await buildOverlayRuntime();
  await copyFile(overlayRuntimePath, overlayRuntimeOutputPath);

  const html = await readFile(generatedHtmlPath, 'utf8');
  const injected = injectChineseOverlay(
    html,
    `import '${overlayRuntimePublicPath}';`,
  );

  if (injected !== html) {
    await writeFile(generatedHtmlPath, injected, 'utf8');
    console.log('[studio-zh] Chinese overlay injected.');
  }
}

export function startChineseMastraDev(): void {
  const child = spawn('npx', buildMastraDevArgs(), {
    cwd: projectRoot,
    env: process.env,
    stdio: 'inherit',
  });

  let interval: NodeJS.Timeout | undefined;

  function stop(signal: NodeJS.Signals): void {
    if (interval) {
      clearInterval(interval);
    }
    child.kill(signal);
  }

  process.on('SIGINT', () => stop('SIGINT'));
  process.on('SIGTERM', () => stop('SIGTERM'));

  interval = setInterval(() => {
    injectOverlay().catch((error: unknown) => {
      console.error('[studio-zh] Failed to inject overlay:', error);
    });
  }, 1000);

  child.on('exit', (code, signal) => {
    if (interval) {
      clearInterval(interval);
    }

    if (signal) {
      process.kill(process.pid, signal);
    }

    process.exit(code ?? 0);
  });
}

if (fileURLToPath(import.meta.url) === resolve(process.argv[1] ?? '')) {
  startChineseMastraDev();
}
