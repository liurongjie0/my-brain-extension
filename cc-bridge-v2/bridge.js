#!/usr/bin/env node
/**
 * Claude Code → Langfuse Bridge v2
 * 消息级粒度实时监听，支持增量同步
 */

const fs = require('fs');
const path = require('path');

// ─── Config ───────────────────────────────────────────────────────────────

const CONFIG_PATH = path.join(__dirname, 'config.json');
const STATE_PATH = path.join(__dirname, 'state.json');
const CFG = JSON.parse(fs.readFileSync(CONFIG_PATH, 'utf8'));
const BASE_URL = CFG.langfuse.host.replace(/\/$/, '');
const AUTH = 'Basic ' + Buffer.from(`${CFG.langfuse.publicKey}:${CFG.langfuse.secretKey}`).toString('base64');
const WATCH_DIR = CFG.watchDir.replace(/^~/, process.env.HOME);
const USER_ID = CFG.userId;

// ─── State ─────────────────────────────────────────────────────────────────

let state = { offsets: {}, sessions: {} };
try { state = JSON.parse(fs.readFileSync(STATE_PATH, 'utf8')); } catch { }

function saveState() {
  fs.writeFileSync(STATE_PATH, JSON.stringify(state, null, 2));
}

// ─── Langfuse API ──────────────────────────────────────────────────────────

async function ingest(batch) {
  const res = await fetch(`${BASE_URL}/api/public/ingestion`, {
    method: 'POST',
    headers: { Authorization: AUTH, 'Content-Type': 'application/json' },
    body: JSON.stringify({ batch }),
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`HTTP ${res.status}: ${text}`);
  }
  return res.json();
}

// ─── Message Parser ────────────────────────────────────────────────────────

function extractContent(msg) {
  const blocks = msg.message?.content;
  if (typeof blocks === 'string') return [{ type: 'text', text: blocks }];
  if (!Array.isArray(blocks)) return null;

  const results = [];
  for (const b of blocks) {
    if (b.type === 'text' && b.text) results.push({ type: 'text', text: b.text });
    if (b.type === 'thinking') results.push({ type: 'thinking', text: b.thinking || b.text || '' });
    if (b.type === 'tool_use') results.push({ type: 'tool_use', name: b.name, input: b.input, id: b.id });
    if (b.type === 'tool_result') results.push({ type: 'tool_result', tool_use_id: b.tool_use_id, content: b.content, is_error: b.is_error || false });
  }
  return results;
}

function shouldIgnore(msg) {
  const ignoreTypes = ['last-prompt', 'permission-mode'];
  if (ignoreTypes.includes(msg.type)) return true;
  return false;
}

function messageToObservation(msg, seq, fallbackSessionId) {
  const sessionId = msg.sessionId || fallbackSessionId;
  const traceId = sessionId;
  const ts = msg.timestamp ? new Date(msg.timestamp) : new Date();
  const obsId = msg.uuid || `${sessionId}-${ts.getTime()}-${seq}`;
  const timestamp = ts.toISOString();

  // attachment (skill listing, permissions, etc.)
  if (msg.type === 'attachment') {
    const content = msg.attachment || msg.message;
    return [{
      id: obsId,
      timestamp,
      type: 'observation-create',
      body: {
        id: obsId,
        traceId,
        type: 'SPAN',
        name: `attachment:${content?.type || 'unknown'}`,
        input: null,
        output: typeof content === 'object' ? JSON.stringify(content).substring(0, 5000) : String(content),
        startTime: timestamp,
        endTime: timestamp,
        metadata: { attachmentType: content?.type, isMeta: true },
      },
    }];
  }

  // system messages
  if (msg.type === 'system') {
    return [{
      id: obsId,
      timestamp,
      type: 'observation-create',
      body: {
        id: obsId,
        traceId,
        type: 'SPAN',
        name: 'system',
        input: null,
        output: JSON.stringify(msg).substring(0, 2000),
        startTime: timestamp,
        endTime: timestamp,
        metadata: { isMeta: true },
      },
    }];
  }

  // file-history-snapshot
  if (msg.type === 'file-history-snapshot') {
    const snap = msg.snapshot || {};
    return [{
      id: obsId,
      timestamp,
      type: 'observation-create',
      body: {
        id: obsId,
        traceId,
        type: 'SPAN',
        name: 'file-history-snapshot',
        input: null,
        output: `tracked files: ${Object.keys(snap.trackedFileBackups || {}).join(', ') || 'none'}`,
        startTime: timestamp,
        endTime: timestamp,
        metadata: { isSnapshotUpdate: msg.isSnapshotUpdate, isMeta: true },
      },
    }];
  }

  // user message (including isMeta=true)
  if (msg.type === 'user') {
    const contents = extractContent(msg);
    const observations = [];

    // isMeta messages (skill arguments, etc.)
    if (msg.isMeta === true) {
      const text = contents?.map(c => c.text).join('\n') || JSON.stringify(msg.message || msg).substring(0, 2000);
      observations.push({
        id: obsId,
        timestamp,
        type: 'observation-create',
        body: {
          id: obsId,
          traceId,
          type: 'SPAN',
          name: msg.promptId ? 'skill-invocation' : 'user-meta',
          input: null,
          output: text,
          startTime: timestamp,
          endTime: timestamp,
          metadata: { project: msg.cwd, branch: msg.gitBranch, promptId: msg.promptId, isMeta: true },
        },
      });
      return observations;
    }

    if (!contents || contents.length === 0) return null;

    const toolResults = contents.filter(c => c.type === 'tool_result');
    const texts = contents.filter(c => c.type === 'text' || c.type === 'string');

    // user text input
    if (texts.length > 0) {
      const text = texts.map(t => t.text).join('\n');
      observations.push({
        id: obsId,
        timestamp,
        type: 'observation-create',
        body: {
          id: obsId,
          traceId,
          type: 'SPAN',
          name: 'user-input',
          input: text.substring(0, 500),
          output: text,
          startTime: timestamp,
          endTime: timestamp,
          metadata: { project: msg.cwd, branch: msg.gitBranch, version: msg.version },
        },
      });
    }

    // tool results
    for (const tr of toolResults) {
      observations.push({
        id: `${obsId}-tr-${tr.tool_use_id?.slice(-8) || seq}`,
        timestamp,
        type: 'observation-create',
        body: {
          id: `${obsId}-tr-${tr.tool_use_id?.slice(-8) || seq}`,
          traceId,
          type: 'SPAN',
          name: 'tool-result',
          input: tr.tool_use_id,
          output: typeof tr.content === 'string' ? tr.content : JSON.stringify(tr.content).substring(0, 2000),
          startTime: timestamp,
          endTime: timestamp,
          metadata: { isError: tr.is_error, toolUseId: tr.tool_use_id },
        },
      });
    }

    return observations;
  }

  // assistant message
  if (msg.type === 'assistant') {
    const contents = extractContent(msg);
    if (!contents || contents.length === 0) return null;

    const observations = [];
    let obsIndex = 0;

    for (const c of contents) {
      const subId = `${obsId}-${obsIndex}`;
      if (c.type === 'thinking') {
        observations.push({
          id: subId,
          timestamp,
          type: 'observation-create',
          body: {
            id: subId,
            traceId,
            type: 'SPAN',
            name: 'thinking',
            input: c.text.substring(0, 200),
            output: c.text,
            startTime: timestamp,
            endTime: timestamp,
            metadata: { model: msg.message?.model },
          },
        });
      } else if (c.type === 'tool_use') {
        observations.push({
          id: subId,
          timestamp,
          type: 'observation-create',
          body: {
            id: subId,
            traceId,
            type: 'SPAN',
            name: `tool:${c.name}`,
            input: typeof c.input === 'object' ? JSON.stringify(c.input) : c.input,
            output: null,
            startTime: timestamp,
            endTime: timestamp,
            metadata: { toolId: c.id, toolName: c.name },
          },
        });
      } else if (c.type === 'text') {
        // 检查是否是最终回复（后面没有 tool_use 了）
        const hasMoreTools = contents.slice(obsIndex + 1).some(x => x.type === 'tool_use');
        observations.push({
          id: subId,
          timestamp,
          type: 'observation-create',
          body: {
            id: subId,
            traceId,
            type: hasMoreTools ? 'SPAN' : 'GENERATION',
            name: hasMoreTools ? 'intermediate-response' : 'response',
            input: null,
            output: c.text,
            startTime: timestamp,
            endTime: timestamp,
            metadata: { model: msg.message?.model },
          },
        });
      }
      obsIndex++;
    }

    return observations;
  }

  return null;
}

function buildTraceCreate(sessionId, firstMsg) {
  const ts = firstMsg.timestamp ? new Date(firstMsg.timestamp) : new Date();
  return {
    id: `trace-${sessionId}`,
    timestamp: ts.toISOString(),
    type: 'trace-create',
    body: {
      id: sessionId,
      name: 'claude-session',
      userId: USER_ID,
      sessionId,
      metadata: {
        project: firstMsg.cwd,
        branch: firstMsg.gitBranch,
        version: firstMsg.version,
        entrypoint: firstMsg.entrypoint,
      },
    },
  };
}

// ─── File Processing ───────────────────────────────────────────────────────

function getJsonlFiles(dir) {
  const files = [];
  for (const entry of fs.readdirSync(dir)) {
    const full = path.join(dir, entry);
    const stat = fs.statSync(full);
    if (stat.isDirectory()) {
      files.push(...getJsonlFiles(full));
    } else if (entry.endsWith('.jsonl')) {
      files.push(full);
    }
  }
  return files;
}

async function processFile(filePath) {
  const relPath = path.relative(WATCH_DIR, filePath);
  const stat = fs.statSync(filePath);
  const saved = state.offsets[relPath] || { lines: 0, mtime: 0 };

  if (stat.mtimeMs <= saved.mtime && saved.lines > 0) return 0;

  const content = fs.readFileSync(filePath, 'utf8');
  const lines = content.trim().split('\n').filter(Boolean);
  const newLines = lines.slice(saved.lines);
  if (newLines.length === 0) return 0;

  // derive sessionId from filename (e.g., "session-id.jsonl")
  const fallbackSessionId = path.basename(filePath, '.jsonl');

  const batch = [];
  const seenSessions = new Set();

  for (let i = 0; i < newLines.length; i++) {
    let msg;
    try { msg = JSON.parse(newLines[i]); } catch { continue; }
    if (shouldIgnore(msg)) continue;

    const sessionId = msg.sessionId || fallbackSessionId;

    // create trace if first time seeing this session
    if (!state.sessions[sessionId] && !seenSessions.has(sessionId)) {
      seenSessions.add(sessionId);
      batch.push(buildTraceCreate(sessionId, msg));
      state.sessions[sessionId] = { createdAt: Date.now() };
    }

    const observations = messageToObservation(msg, saved.lines + i, fallbackSessionId);
    if (observations) batch.push(...observations);
  }

  if (batch.length > 0) {
    try {
      const result = await ingest(batch);
      if (result.errors && result.errors.length > 0) {
        console.error(`  ❌ ${result.errors.length} errors:`, result.errors[0].message);
      } else {
        process.stdout.write(`.${batch.length}`);
      }
    } catch (err) {
      console.error(`\n  ❌ ${err.message} | ${relPath}`);
      return 0; // don't advance offset on error
    }
  }

  state.offsets[relPath] = { lines: lines.length, mtime: stat.mtimeMs };
  saveState();
  return newLines.length;
}

// ─── Watcher ───────────────────────────────────────────────────────────────

let isScanning = false;

async function scanAll() {
  if (isScanning) return;
  isScanning = true;

  try {
    const files = getJsonlFiles(WATCH_DIR);
    let totalNew = 0;
    for (const fp of files) {
      totalNew += await processFile(fp);
    }
    if (totalNew > 0) {
      console.log(`\n  [${new Date().toLocaleTimeString()}] ${totalNew} new messages from ${files.length} files`);
    }
  } finally {
    isScanning = false;
  }
}

async function main() {
  console.log('🔌 Claude Code → Langfuse Bridge v2');
  console.log(`   Host: ${BASE_URL}`);
  console.log(`   Watch: ${WATCH_DIR}`);
  console.log(`   User: ${USER_ID}`);
  console.log(`   Poll interval: ${CFG.pollIntervalMs}ms\n`);

  // initial scan
  await scanAll();

  // watch directory tree
  fs.watch(WATCH_DIR, { recursive: true }, (eventType, filename) => {
    if (filename && filename.endsWith('.jsonl')) {
      // debounce: scan after a short delay
      setTimeout(scanAll, 500);
    }
  });

  // periodic poll as fallback
  setInterval(scanAll, CFG.pollIntervalMs);

  console.log('👀 Watching for changes... (Ctrl+C to stop)\n');
}

main().catch(err => {
  console.error('❌ Fatal:', err.message);
  process.exit(1);
});
