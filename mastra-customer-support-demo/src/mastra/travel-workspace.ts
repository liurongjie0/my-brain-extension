import { fileURLToPath } from 'node:url';
import { LocalFilesystem, Workspace } from '@mastra/core/workspace';

// Resolve relative to this file so the workspace works no matter which
// directory Studio, vitest, or the demo scripts are started from.
export const travelWorkspaceRoot = fileURLToPath(
  new URL('../../workspace', import.meta.url),
);

export const travelWorkspace = new Workspace({
  filesystem: new LocalFilesystem({ basePath: travelWorkspaceRoot }),
  skills: ['skills'],
});
