import { afterEach, describe, expect, it, vi } from 'vitest';

vi.mock('node:child_process', () => ({
  spawn: vi.fn(() => ({
    kill: vi.fn(),
    on: vi.fn(),
  })),
}));

describe('buildMastraDevArgs', () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.resetModules();
  });

  it('starts Mastra dev with request context presets', async () => {
    vi.useFakeTimers();
    const devZhModule = (await import('./dev-zh.ts')) as {
      buildMastraDevArgs?: () => string[];
    };

    expect(devZhModule.buildMastraDevArgs?.()).toEqual([
      'mastra',
      'dev',
      '--request-context-presets',
      'request-context-presets.json',
    ]);
  });
});
