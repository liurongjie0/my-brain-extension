package com.agentplatform.tool;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * Built-in "plan-as-a-tool" callback (the TodoWrite pattern). The model calls
 * {@code write_todos} with a checklist to record/refresh its plan for a multi-step
 * task; the call itself does no real work — it just acknowledges — and the orchestrator
 * surfaces the checklist to the client as a live plan card. This keeps the ReAct loop on
 * track and gives the user visible progress without a separate planner stage.
 */
public class PlanToolCallback implements ToolCallback {

    public static final String NAME = "write_todos";

    private static final String SCHEMA = """
            {
              "type": "object",
              "properties": {
                "todos": {
                  "type": "array",
                  "description": "the full task checklist, re-sent in full on every update",
                  "items": {
                    "type": "object",
                    "properties": {
                      "content": { "type": "string", "description": "what this step does" },
                      "status": { "type": "string", "enum": ["pending", "in_progress", "completed"] }
                    },
                    "required": ["content", "status"]
                  }
                }
              },
              "required": ["todos"]
            }""";

    @Override
    public ToolDefinition getToolDefinition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description("Record or update your task checklist for a multi-step task. "
                        + "Call this before starting multi-step work to lay out the steps, then "
                        + "call it again to mark steps in_progress/completed as you go. Always send "
                        + "the complete list. Skip it for simple one-step questions.")
                .inputSchema(SCHEMA)
                .build();
    }

    @Override
    public String call(String toolInput) {
        // state lives client-side (rendered as a plan card); nothing to execute here
        return "ok";
    }
}
