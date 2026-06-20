import { describe, it, expect } from 'vitest'
import { parseSseChunk } from './sse.js'

describe('parseSseChunk', () => {
  it('parses complete events and keeps leftover', () => {
    const input = 'event:meta\ndata:{"conversationId":1}\n\nevent:token\ndata:Hello\n\nevent:tok'
    const { events, rest } = parseSseChunk(input)
    expect(events).toHaveLength(2)
    expect(events[0].event).toBe('meta')
    expect(events[1].data).toBe('Hello')
    expect(rest).toBe('event:tok')
  })
})
