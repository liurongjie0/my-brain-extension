import {
  MastraStorageExporter,
  Observability,
  SamplingStrategyType,
} from '@mastra/observability';

export const supportObservabilityServiceName = 'mastra-customer-support-demo';

export const supportObservabilityRequestContextKeys = [
  'locale',
  'customerTier',
  'tenantId',
  'supportChannel',
];

export const supportObservability = new Observability({
  configs: {
    default: {
      serviceName: supportObservabilityServiceName,
      sampling: { type: SamplingStrategyType.ALWAYS },
      exporters: [
        new MastraStorageExporter({
          maxBatchSize: 1,
          maxBatchWaitMs: 250,
        }),
      ],
      requestContextKeys: supportObservabilityRequestContextKeys,
      logging: {
        enabled: true,
        level: 'info',
      },
    },
  },
});
