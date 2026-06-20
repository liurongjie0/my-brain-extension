package com.agentplatform.orchestrator;

/** SSE event types emitted by the orchestrator (also used as ChatChunk.type). */
public final class ChatEvents {
    public static final String META = "meta";
    public static final String SOURCE = "source";
    public static final String STEP = "step";
    public static final String TOKEN = "token";
    public static final String DONE = "done";
    public static final String ERROR = "error";

    private ChatEvents() {}
}
