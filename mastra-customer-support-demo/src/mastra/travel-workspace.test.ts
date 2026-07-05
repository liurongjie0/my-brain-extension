import { describe, expect, it } from 'vitest';
import { travelWorkspace } from './travel-workspace.ts';

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
