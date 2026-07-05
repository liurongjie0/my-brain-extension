import { mkdirSync } from 'node:fs';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { LibSQLStore } from '@mastra/libsql';

const supportStorageDir = resolve(process.cwd(), '.mastra');

mkdirSync(supportStorageDir, { recursive: true });

export const supportStorageUrl = pathToFileURL(
  resolve(supportStorageDir, 'support-demo.db'),
).href;

export const supportStorage = new LibSQLStore({
  id: 'support-demo-storage',
  url: supportStorageUrl,
});
