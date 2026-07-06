import { mkdirSync } from 'node:fs';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import {
  FilesystemStore,
  InMemoryStore,
  MastraCompositeStore,
} from '@mastra/core/storage';
import { DuckDBStore } from '@mastra/duckdb';
import { LibSQLStore } from '@mastra/libsql';

const supportStorageDir = resolve(process.cwd(), '.mastra');

mkdirSync(supportStorageDir, { recursive: true });

export const supportStorageUrl = pathToFileURL(
  resolve(supportStorageDir, 'support-demo.db'),
).href;

const supportLibsqlStore = new LibSQLStore({
  id: 'support-demo-libsql',
  url: supportStorageUrl,
});

// LibSQL does not support metrics collection (row-oriented), so the
// observability domain (traces/metrics/logs shown in Studio) goes to an
// embedded OLAP DuckDB file and survives restarts. Everything else
// (threads, memory, workflow runs) stays in LibSQL.
// DuckDB holds a single-process write lock, so parallel vitest workers fall
// back to in-memory observability instead of fighting over the file.
const supportObservabilityDomain = process.env.VITEST
  ? new InMemoryStore({ id: 'support-demo-observability' }).stores.observability
  : new DuckDBStore({
      id: 'support-demo-observability',
      path: resolve(supportStorageDir, 'support-demo-observability.duckdb'),
    }).observability;

// Editor domains (Studio-edited agents, prompt blocks, MCP configs, ...) are
// stored as JSON files in the repo so edits are reviewable via git.
const supportEditorStore = new FilesystemStore({
  dir: resolve(process.cwd(), 'mastra-editor'),
});

export const supportStorage = new MastraCompositeStore({
  id: 'support-demo-storage',
  default: supportLibsqlStore,
  editor: supportEditorStore,
  domains: {
    observability: supportObservabilityDomain,
  },
});
