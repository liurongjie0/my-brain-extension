package com.agentplatform.tool;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ToolCallbackFactory {

    private final ToolRepository repository;
    private final HttpToolExecutor executor;

    public ToolCallbackFactory(ToolRepository repository, HttpToolExecutor executor) {
        this.repository = repository;
        this.executor = executor;
    }

    public List<ToolCallback> build(List<Long> toolIds) {
        List<ToolCallback> callbacks = new ArrayList<>();
        for (ToolEntity t : repository.findAllById(toolIds)) {
            if (Boolean.TRUE.equals(t.getEnabled())) {
                callbacks.add(new DynamicHttpToolCallback(t, executor));
            }
        }
        return callbacks;
    }
}
