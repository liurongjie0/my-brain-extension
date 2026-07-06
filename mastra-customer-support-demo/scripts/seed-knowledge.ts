// Build the travel-guides vector index from knowledge/*.md using local
// fastembed embeddings (no API key required). Re-runnable: the index is
// recreated from scratch each time. Ends with a sample query as self-check.
import { readFile, readdir } from 'node:fs/promises';
import { resolve } from 'node:path';
import { MDocument } from '@mastra/rag';
import {
  travelEmbedder,
  travelEmbeddingDimension,
  travelKnowledgeIndexName,
  travelVector,
} from '../src/mastra/travel-vector.ts';

const knowledgeDir = resolve(process.cwd(), 'knowledge');

const files = (await readdir(knowledgeDir)).filter((file) => file.endsWith('.md'));
if (files.length === 0) {
  throw new Error(`No markdown files found in ${knowledgeDir}`);
}

interface SeedChunk {
  text: string;
  source: string;
}

const chunks: SeedChunk[] = [];
for (const file of files) {
  const content = await readFile(resolve(knowledgeDir, file), 'utf8');
  const doc = MDocument.fromMarkdown(content);
  // Split per ## section so retrieval returns the relevant passage,
  // not the whole guide.
  const docChunks = await doc.chunk({
    strategy: 'markdown',
    headers: [
      ['#', 'guide'],
      ['##', 'section'],
    ],
  });
  for (const chunk of docChunks) {
    chunks.push({ text: chunk.text, source: file });
  }
}

console.log(`Chunked ${files.length} guides into ${chunks.length} chunks.`);

const { embeddings } = await travelEmbedder.doEmbed({
  values: chunks.map((chunk) => chunk.text),
});

const existing = await travelVector.listIndexes();
if (existing.includes(travelKnowledgeIndexName)) {
  await travelVector.deleteIndex({ indexName: travelKnowledgeIndexName });
}
await travelVector.createIndex({
  indexName: travelKnowledgeIndexName,
  dimension: travelEmbeddingDimension,
});
await travelVector.upsert({
  indexName: travelKnowledgeIndexName,
  vectors: embeddings.map((embedding) => [...embedding]),
  metadata: chunks.map((chunk) => ({ text: chunk.text, source: chunk.source })),
});

console.log(`Indexed ${chunks.length} chunks into "${travelKnowledgeIndexName}".`);

// Self-check: retrieve for a question the mock data cannot answer.
const sampleQuestion = '徽杭古道哪里可以补水？';
const { embeddings: queryEmbeddings } = await travelEmbedder.doEmbed({
  values: [sampleQuestion],
});
const results = await travelVector.query({
  indexName: travelKnowledgeIndexName,
  queryVector: [...queryEmbeddings[0]],
  topK: 2,
});

console.log(`\nSample query: ${sampleQuestion}`);
for (const result of results) {
  const preview = String(result.metadata?.text ?? '').slice(0, 60);
  console.log(`  score=${result.score.toFixed(3)} [${result.metadata?.source}] ${preview}…`);
}

await travelVector.close();
