package com.agentplatform.orchestrator;

/** SSE event types emitted by the orchestrator (also used as ChatChunk.type). */
public final class ChatEvents {
    public static final String META = "meta";
    public static final String SOURCE = "source";
    public static final String STEP = "step";
    /** task checklist snapshot from the write_todos tool, rendered as a live plan card */
    public static final String PLAN = "plan";
    public static final String TOKEN = "token";
    /** reasoning / thinking delta (e.g. DeepSeek reasoning_content), rendered collapsed */
    public static final String THINK = "think";
    public static final String DONE = "done";
    public static final String ERROR = "error";

    private ChatEvents() {}
}
