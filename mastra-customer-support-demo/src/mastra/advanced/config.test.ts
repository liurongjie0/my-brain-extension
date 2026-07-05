import { describe, expect, it } from 'vitest';
import { supportMemory, supportMemoryOptions } from './memory.ts';
import {
  supportObservability,
  supportObservabilityRequestContextKeys,
  supportObservabilityServiceName,
} from './observability.ts';
import { supportStorage, supportStorageUrl } from './storage.ts';

describe('advanced Mastra config', () => {
  it('uses a local ignored LibSQL file for durable demo data', () => {
    expect(supportStorageUrl).toMatch(/^file:\/.*\/\.mastra\/support-demo\.db$/);
    expect(supportStorage).toBeDefined();
  });

  it('enables conversational memory with working-memory support', () => {
    expect(supportMemory.id).toBe('support-memory');
    expect(supportMemoryOptions.options?.lastMessages).toBe(20);
    expect(supportMemoryOptions.options?.workingMemory).toMatchObject({
      enabled: true,
      scope: 'resource',
    });
  });

  it('configures storage-backed observability for support traces', () => {
    expect(supportObservabilityServiceName).toBe(
      'mastra-customer-support-demo',
    );
    expect(supportObservabilityRequestContextKeys).toEqual([
      'locale',
      'customerTier',
      'tenantId',
      'supportChannel',
    ]);
    expect(supportObservability).toBeDefined();
  });
});
