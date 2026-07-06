import { mkdirSync } from 'node:fs';
import { resolve } from 'node:path';
import { DuckDBVector } from '@mastra/duckdb';
import { fastembed } from '@mastra/fastembed';

const vectorDir = resolve(process.cwd(), '.mastra');

mkdirSync(vectorDir, { recursive: true });

export const travelKnowledgeIndexName = 'travel_guides';

// bge-small produces 384-dimensional embeddings; runs locally via ONNX,
// so RAG and semantic recall work without any embedding API key.
export const travelEmbedder = fastembed;
export const travelEmbeddingDimension = 384;

// One shared DuckDBVector instance (single connection, single file) serves
// both the knowledge index and memory semantic recall — two instances on the
// same file would fight over DuckDB's native write lock.
export const travelVector = new DuckDBVector({
  id: 'travel-vector',
  path: resolve(vectorDir, 'support-demo-vectors.duckdb'),
});
