package com.agentplatform.orchestrator;

import com.agentplatform.tool.PlanToolCallback;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * The agent turn as an explicit Spring AI Alibaba {@link StateGraph}:
 *
 * <pre>
 *   START → agent ──(tool_calls &amp; steps&lt;MAX)──→ tools ──┐
 *             ↑                                            │
 *             └────────────────────────────────────────────┘
 *             └──(no tool_calls / steps exhausted)──→ END
 * </pre>
 *
 * One unified graph serves every agent type: agents with no tools simply route
 * straight to END after the first streamed turn; tool/react agents loop through the
 * tools node. The graph owns the control flow (nodes + conditional edge + shared
 * {@link OverAllState}); the nodes stream tokens / emit step+plan events to a sink and
 * record what must be persisted into a per-run {@link Run} accumulator.
 */
@Component
public class AgentGraph {

    private static final int MAX_STEPS = 8;

    // OverAllState keys (control-flow data that flows between nodes)
    static final String MESSAGES = "messages";       // List<Message> — the running conversation
    static final String STEPS = "steps";             // Integer — tool-loop counter
    static final String HAS_TOOLS = "hasTools";      // Boolean — last agent turn requested tools
    static final String TOOL_RESP = "toolResp";      // ChatResponse — the tool-bearing turn
    static final String LAST_PROMPT = "lastPrompt";  // Prompt — what produced toolResp

    private final ToolCallingManager toolCallingManager;
    private final ObjectMapper objectMapper;

    public AgentGraph(ToolCallingManager toolCallingManager, ObjectMapper objectMapper) {
        this.toolCallingManager = toolCallingManager;
        this.objectMapper = objectMapper;
    }

    /** Per-run accumulator: the persistence-facing output the nodes fill in as they execute. */
    public static final class Run {
        final StringBuilder finalText = new StringBuilder();
        final List<Map<String, Object>> trajectory = new ArrayList<>();
        Integer usage;

        public String finalText() { return finalText.toString(); }
        public List<Map<String, Object>> trajectory() { return trajectory; }
        public Integer usage() { return usage; }
    }

    /**
     * Per-request context the nodes close over: who to call, with what options, where to emit,
     * plus a cancellation flag + signal so a client "stop" actually halts the model stream
     * and breaks the loop (instead of the server running the turn to completion).
     */
    public record Ctx(Long convId, ChatModel model, OpenAiChatOptions options,
                      Consumer<ChatChunk> emit, Run run,
                      AtomicBoolean cancelled, Mono<Void> cancelSignal) {}

    /** Build + compile a fresh graph for one request. */
    public CompiledGraph build(Ctx ctx) throws GraphStateException {
        OverAllState state = new OverAllState();
        state.registerKeyAndStrategy(MESSAGES, new ReplaceStrategy());
        state.registerKeyAndStrategy(STEPS, new ReplaceStrategy());
        state.registerKeyAndStrategy(HAS_TOOLS, new ReplaceStrategy());
        state.registerKeyAndStrategy(TOOL_RESP, new ReplaceStrategy());
        state.registerKeyAndStrategy(LAST_PROMPT, new ReplaceStrategy());

        StateGraph graph = new StateGraph(state)
                .addNode("agent", node_async(agentNode(ctx)))
                .addNode("tools", node_async(toolsNode(ctx)))
                .addEdge(StateGraph.START, "agent")
                .addConditionalEdges("agent", edge_async(routeEdge(ctx)),
                        Map.of("tools", "tools", "end", StateGraph.END))
                .addEdge("tools", "agent");

        CompiledGraph compiled = graph.compile();
        compiled.setMaxIterations(2 * MAX_STEPS + 4); // well above the agent↔tools loop bound
        return compiled;
    }

    // ===== nodes =====

    /** Stream one model turn: emit token/think deltas live, stash the tool-bearing response. */
    private NodeAction agentNode(Ctx ctx) {
        return state -> {
            // client already stopped before this turn started → produce nothing, route to END
            if (ctx.cancelled().get()) {
                Map<String, Object> out = new HashMap<>();
                out.put(HAS_TOOLS, false);
                return out;
            }
            List<Message> msgs = state.<List<Message>>value(MESSAGES).orElseGet(List::of);
            Prompt prompt = new Prompt(new ArrayList<>(msgs), ctx.options());

            StringBuilder turnText = new StringBuilder();
            List<ChatResponse> chunks = new ArrayList<>();
            ctx.model().stream(prompt)
                    // a client "stop" completes cancelSignal → truncates this stream and cancels
                    // the upstream HTTP request, so generation (and token spend) actually halts
                    .takeUntilOther(ctx.cancelSignal())
                    .doOnNext(resp -> {
                        chunks.add(resp);
                        String think = reasoningOf(resp);
                        if (!think.isEmpty()) ctx.emit().accept(new ChatChunk(ctx.convId(), ChatEvents.THINK, think));
                        String t = textOf(resp);
                        if (!t.isEmpty()) {
                            turnText.append(t);
                            ctx.emit().accept(new ChatChunk(ctx.convId(), ChatEvents.TOKEN, t));
                        }
                    })
                    .blockLast();

            ChatResponse toolResp = lastWithToolCalls(chunks);
            ChatResponse lastResp = chunks.isEmpty() ? null : chunks.get(chunks.size() - 1);
            Integer u = usageOf(lastResp);
            if (u != null) ctx.run().usage = u;
            // the final answer is whatever the LAST agent turn streams (a tool turn's narration
            // gets overwritten by the next turn; the turn routed to END wins)
            ctx.run().finalText.setLength(0);
            ctx.run().finalText.append(turnText);

            Map<String, Object> out = new HashMap<>();
            out.put(HAS_TOOLS, toolResp != null);
            out.put(LAST_PROMPT, prompt);
            if (toolResp != null) out.put(TOOL_RESP, toolResp);
            return out;
        };
    }

    /** Execute the requested tools, surfacing each as a step (or plan) event. */
    private NodeAction toolsNode(Ctx ctx) {
        return state -> {
            ChatResponse toolResp = state.<ChatResponse>value(TOOL_RESP).orElseThrow();
            Prompt lastPrompt = state.<Prompt>value(LAST_PROMPT).orElseThrow();
            int steps = state.value(STEPS, 0);

            List<AssistantMessage.ToolCall> calls = toolResp.getResult().getOutput().getToolCalls();
            ToolExecutionResult execResult = toolCallingManager.executeToolCalls(lastPrompt, toolResp);
            Map<String, String> resultsById = extractToolResults(execResult);

            for (AssistantMessage.ToolCall call : calls) {
                String args = call.arguments() == null ? "" : call.arguments();

                // the planning tool isn't a real action — surface its checklist as a plan card
                if (PlanToolCallback.NAME.equals(call.name())) {
                    Object todos = extractTodos(args);
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("tool", PlanToolCallback.NAME);
                    entry.put("plan", todos);
                    ctx.run().trajectory.add(entry);
                    try {
                        ctx.emit().accept(new ChatChunk(ctx.convId(), ChatEvents.PLAN, objectMapper.writeValueAsString(todos)));
                    } catch (Exception e) {
                        ctx.emit().accept(new ChatChunk(ctx.convId(), ChatEvents.PLAN, "[]"));
                    }
                    continue;
                }

                String result = resultsById.getOrDefault(call.id(), "");
                String shortResult = truncate(result, 300);
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("tool", call.name());
                entry.put("args", args);
                entry.put("result", shortResult);
                String stepJson;
                try {
                    stepJson = objectMapper.writeValueAsString(entry);
                } catch (Exception e) {
                    stepJson = "调用 " + call.name() + "(" + args + ") => " + shortResult;
                }
                ctx.emit().accept(new ChatChunk(ctx.convId(), ChatEvents.STEP, stepJson));
                ctx.run().trajectory.add(entry);
            }

            Map<String, Object> out = new HashMap<>();
            out.put(MESSAGES, execResult.conversationHistory());
            out.put(STEPS, steps + 1);
            return out;
        };
    }

    /** Loop back into tools while the last turn asked for them and the step budget remains. */
    private EdgeAction routeEdge(Ctx ctx) {
        return state -> {
            if (ctx.cancelled().get()) return "end";  // stop the loop on client cancel
            boolean hasTools = state.value(HAS_TOOLS, false);
            int steps = state.value(STEPS, 0);
            return (hasTools && steps < MAX_STEPS) ? "tools" : "end";
        };
    }

    // ===== helpers =====

    private String textOf(ChatResponse resp) {
        if (resp == null || resp.getResult() == null || resp.getResult().getOutput() == null) return "";
        String t = resp.getResult().getOutput().getText();
        return t != null ? t : "";
    }

    /** Reasoning delta if the model surfaces one (e.g. DeepSeek reasoning_content); "" otherwise. */
    private String reasoningOf(ChatResponse resp) {
        if (resp == null || resp.getResult() == null) return "";
        AssistantMessage out = resp.getResult().getOutput();
        if (out == null || out.getMetadata() == null) return "";
        Object r = out.getMetadata().get("reasoningContent");
        if (r == null) r = out.getMetadata().get("reasoning_content");
        return r != null ? r.toString() : "";
    }

    private ChatResponse lastWithToolCalls(List<ChatResponse> chunks) {
        ChatResponse found = null;
        for (ChatResponse r : chunks) {
            if (r != null && r.hasToolCalls()) found = r;
        }
        return found;
    }

    private Integer usageOf(ChatResponse resp) {
        if (resp == null || resp.getMetadata() == null) return null;
        Usage usage = resp.getMetadata().getUsage();
        return usage != null ? usage.getTotalTokens() : null;
    }

    private Map<String, String> extractToolResults(ToolExecutionResult execResult) {
        Map<String, String> byId = new HashMap<>();
        for (Message m : execResult.conversationHistory()) {
            if (m instanceof ToolResponseMessage trm) {
                for (ToolResponseMessage.ToolResponse r : trm.getResponses()) {
                    byId.put(r.id(), r.responseData());
                }
            }
        }
        return byId;
    }

    /** Pull the {@code todos} array out of a write_todos call's JSON args; [] if malformed. */
    private Object extractTodos(String args) {
        try {
            Map<?, ?> m = objectMapper.readValue(args, Map.class);
            Object todos = m.get("todos");
            if (todos != null) return todos;
        } catch (Exception ignored) {
            // fall through to empty plan
        }
        return List.of();
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }
}
