// Parse accumulated SSE text into complete events; return events + leftover.
export function parseSseChunk(buffer) {
  const events = []
  let rest = buffer
  let idx
  while ((idx = rest.indexOf('\n\n')) !== -1) {
    const block = rest.slice(0, idx)
    rest = rest.slice(idx + 2)
    let event = 'message'
    const dataLines = []
    for (const line of block.split('\n')) {
      if (line.startsWith('event:')) event = line.slice(6).trim()
      else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
    }
    if (dataLines.length) events.push({ event, data: dataLines.join('\n') })
  }
  return { events, rest }
}

export async function streamChat(body, { onEvent, onError, onDone, signal }) {
  try {
    const resp = await fetch('/api/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
      body: JSON.stringify(body),
      signal
    })
    // server may answer with a JSON error (e.g. agent disabled / validation) instead of a stream
    const contentType = resp.headers.get('content-type') || ''
    if (!resp.ok || !contentType.includes('text/event-stream')) {
      let message = `请求失败 (${resp.status})`
      try {
        const j = await resp.json()
        if (j && j.message) message = j.message
      } catch (_) {}
      onError && onError(new Error(message))
      return
    }
    const reader = resp.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    for (;;) {
      const { value, done } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const { events, rest } = parseSseChunk(buffer)
      buffer = rest
      for (const e of events) onEvent && onEvent(e)
    }
    onDone && onDone()
  } catch (err) {
    // user-initiated stop: end quietly, not as an error
    if (err && err.name === 'AbortError') { onDone && onDone(); return }
    onError && onError(err)
  }
}
