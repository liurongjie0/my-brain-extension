import { fileURLToPath } from 'node:url';
import {
  LocalFilesystem,
  LocalSandbox,
  Workspace,
} from '@mastra/core/workspace';

// Resolve relative to this file so the workspace works no matter which
// directory Studio, vitest, or the demo scripts are started from.
export const travelWorkspaceRoot = fileURLToPath(
  new URL('../../workspace', import.meta.url),
);

// Sandbox demo: gives the agent execute_command / file tools scoped to a
// scratch directory. LocalSandbox only inherits PATH (no other host env) and
// uses macOS seatbelt isolation where available; swap in @mastra/docker or a
// cloud provider (@mastra/e2b, @mastra/daytona) for hard isolation.
export const travelSandbox = new LocalSandbox({
  id: 'travel-sandbox',
  workingDirectory: fileURLToPath(
    new URL('../../.mastra/sandbox', import.meta.url),
  ),
  timeout: 15_000,
});

export const travelWorkspace = new Workspace({
  filesystem: new LocalFilesystem({ basePath: travelWorkspaceRoot }),
  sandbox: travelSandbox,
  skills: ['skills'],
});
