/**
 * Normalizes dataset items saved via Studio's "Save as Dataset Item" button.
 *
 * Score records store their input as internal payloads, so the button saves
 * shapes the experiment runner cannot feed back into `agent.generate()`:
 *   A. { inputMessages: [...] }                    (live-chat score)
 *   B. { input: { inputMessages: [...] }, ... }    (score of a failed run)
 *   C. { input: "text", output: {...}, ... }       (experiment score)
 *
 * This script rewrites every such item to the plain user-message string the
 * runner expects, and fills the item's groundTruth from the payload when the
 * item has none.
 *
 * Usage: npm run fix:datasets   (dev server must be running)
 */

export interface NormalizedItemInput {
  input: string;
  groundTruth?: unknown;
}

function lastUserText(messages: unknown[]): string | null {
  for (let i = messages.length - 1; i >= 0; i--) {
    const message = messages[i] as Record<string, unknown> | null;
    if (!message || message.role !== 'user') {
      continue;
    }

    const content = message.content;
    if (typeof content === 'string') {
      return content;
    }

    if (content && typeof content === 'object') {
      const record = content as Record<string, unknown>;
      if (typeof record.content === 'string') {
        return record.content;
      }
      if (Array.isArray(record.parts)) {
        const text = record.parts
          .filter(
            (part): part is { type: string; text: string } =>
              !!part &&
              typeof part === 'object' &&
              (part as Record<string, unknown>).type === 'text' &&
              typeof (part as Record<string, unknown>).text === 'string',
          )
          .map((part) => part.text)
          .join('\n');
        if (text) {
          return text;
        }
      }
    }
  }

  return null;
}

export function normalizeDatasetItemInput(raw: unknown): NormalizedItemInput | null {
  if (typeof raw === 'string' || !raw || typeof raw !== 'object') {
    return null;
  }

  const obj = raw as Record<string, unknown>;

  // Shape C: { input: "text", output?, groundTruth? }
  if (typeof obj.input === 'string') {
    return { input: obj.input, groundTruth: obj.groundTruth };
  }

  // Shape A: { inputMessages: [...] }
  // Shape B: { input: { inputMessages: [...] } }
  const candidates: Record<string, unknown>[] = [obj];
  if (obj.input && typeof obj.input === 'object') {
    candidates.push(obj.input as Record<string, unknown>);
  }

  for (const candidate of candidates) {
    if (Array.isArray(candidate.inputMessages)) {
      const text = lastUserText(candidate.inputMessages);
      if (text) {
        return { input: text };
      }
    }
  }

  return null;
}

const baseUrl = process.env.MASTRA_BASE_URL ?? 'http://localhost:4111';

async function fetchJson(path: string, init?: RequestInit): Promise<any> {
  const response = await fetch(`${baseUrl}/api${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...init,
  });
  if (!response.ok) {
    throw new Error(`${init?.method ?? 'GET'} ${path} failed: ${response.status}`);
  }
  return response.json();
}

async function normalizeAllDatasets(): Promise<void> {
  const { datasets } = await fetchJson('/datasets?perPage=100');
  let fixed = 0;
  let skipped = 0;

  for (const dataset of datasets) {
    const { items } = await fetchJson(`/datasets/${dataset.id}/items?perPage=100`);
    for (const item of items) {
      const normalized = normalizeDatasetItemInput(item.input);
      if (!normalized) {
        skipped += 1;
        continue;
      }

      const patch: Record<string, unknown> = { input: normalized.input };
      if (item.groundTruth == null && normalized.groundTruth != null) {
        patch.groundTruth = normalized.groundTruth;
      }

      await fetchJson(`/datasets/${dataset.id}/items/${item.id}`, {
        method: 'PATCH',
        body: JSON.stringify(patch),
      });
      fixed += 1;
      console.log(
        `[fix:datasets] ${dataset.name} / ${item.id.slice(0, 8)} -> ${JSON.stringify(normalized.input)}`,
      );
    }
  }

  console.log(`[fix:datasets] done: ${fixed} fixed, ${skipped} already clean.`);
}

if (process.argv[1] && import.meta.url.endsWith(process.argv[1].split('/').pop() ?? '')) {
  normalizeAllDatasets().catch((error) => {
    console.error('[fix:datasets] failed:', error);
    process.exitCode = 1;
  });
}
