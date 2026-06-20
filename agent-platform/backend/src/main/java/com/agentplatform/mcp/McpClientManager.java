package com.agentplatform.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Connects to MCP servers and exposes their tools as Spring AI ToolCallbacks.
 * Clients are cached per server config (rebuilt when the config changes). Only SSE
 * transport is supported for now; stdio is stored but not yet connected.
 */
@Component
public class McpClientManager {

    private final ConcurrentHashMap<String, McpSyncClient> cache = new ConcurrentHashMap<>();

    public List<ToolCallback> toolCallbacks(McpServerEntity server) {
        McpSyncClient client = clientFor(server);
        return Arrays.asList(new SyncMcpToolCallbackProvider(client).getToolCallbacks());
    }

    public List<String> listToolNames(McpServerEntity server) {
        return toolCallbacks(server).stream().map(t -> t.getToolDefinition().name()).toList();
    }

    public void evict(Long serverId) {
        cache.keySet().removeIf(k -> k.startsWith(serverId + "|"));
    }

    private McpSyncClient clientFor(McpServerEntity server) {
        String key = server.getId() + "|" + server.getTransport() + "|" + server.getUrl();
        return cache.computeIfAbsent(key, k -> {
            if (!"sse".equalsIgnoreCase(server.getTransport())) {
                throw new IllegalArgumentException("当前仅支持 sse 传输（stdio 待支持）");
            }
            if (server.getUrl() == null || server.getUrl().isBlank()) {
                throw new IllegalArgumentException("sse 传输需要填写 url");
            }
            McpSyncClient client = McpClient
                    .sync(HttpClientSseClientTransport.builder(server.getUrl()).build())
                    .build();
            client.initialize();
            return client;
        });
    }
}
