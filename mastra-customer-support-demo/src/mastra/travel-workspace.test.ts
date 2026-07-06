import { afterAll, describe, expect, it } from 'vitest';
import { travelSandbox, travelWorkspace } from './travel-workspace.ts';

afterAll(async () => {
  await travelSandbox.stop();
});

describe('travelWorkspace skills', () => {
  it('discovers the hiking-safety skill', async () => {
    const skills = travelWorkspace.skills;
    expect(skills).toBeDefined();

    const list = await skills!.list();
    expect(list.map((skill) => skill.name)).toContain('hiking-safety');
  });

  it('loads the skill body and its reference files', async () => {
    const skill = await travelWorkspace.skills!.get('hiking-safety');
    expect(skill).not.toBeNull();
    expect(skill!.description).toContain('安全');
    expect(skill!.instructions).toContain('STOP');
    expect(skill!.references).toContain('emergency.md');
  });
});

describe('travelWorkspace sandbox', () => {
  it('executes commands in the local sandbox', async () => {
    expect(travelWorkspace.sandbox).toBeDefined();
    expect(travelSandbox.provider).toBe('local');

    const executeCommand = travelSandbox.executeCommand?.bind(travelSandbox);
    expect(executeCommand).toBeDefined();

    const result = await executeCommand!('node', [
      '-e',
      'console.log(9200 * 2 * 2 + 12000)',
    ]);
    expect(result.exitCode).toBe(0);
    expect(result.stdout.trim()).toBe('48800');
  });
});
