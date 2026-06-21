package com.agentplatform.mcp;

import com.agentplatform.tool.SsrfGuard;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
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

    private static final Logger log = LoggerFactory.getLogger(McpClientManager.class);

    private final ConcurrentHashMap<String, McpSyncClient> cache = new ConcurrentHashMap<>();
    private final boolean allowPrivateNetwork;

    public McpClientManager(@Value("${tool.allow-private-network:false}") boolean allowPrivateNetwork) {
        this.allowPrivateNetwork = allowPrivateNetwork;
    }

    public List<ToolCallback> toolCallbacks(McpServerEntity server) {
        McpSyncClient client = clientFor(server);
        return Arrays.asList(new SyncMcpToolCallbackProvider(client).getToolCallbacks());
    }

    public List<String> listToolNames(McpServerEntity server) {
        return toolCallbacks(server).stream().map(t -> t.getToolDefinition().name()).toList();
    }

    public void evict(Long serverId) {
        String prefix = serverId + "|";
        // close evicted clients — they hold SSE long-connections + background threads that
        // GC won't reclaim; removing the reference alone leaks the connection (be-2).
        cache.keySet().stream().filter(k -> k.startsWith(prefix)).toList()
                .forEach(k -> closeQuietly(cache.remove(k)));
    }

    private void closeQuietly(McpSyncClient client) {
        if (client == null) return;
        try {
            client.closeGracefully();
        } catch (Exception e) {
            log.warn("close MCP client failed: {}", e.getMessage());
        }
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
            // SSRF guard: this is a server-side outbound connection to an admin-supplied URL,
            // so it must clear the same internal-address checks as the HTTP tool path (sec-3).
            String blocked = SsrfGuard.check(server.getUrl(), allowPrivateNetwork);
            if (blocked != null) {
                throw new IllegalArgumentException("MCP url 被拒绝: " + blocked);
            }
            McpSyncClient client = McpClient
                    .sync(HttpClientSseClientTransport.builder(server.getUrl()).build())
                    .build();
            client.initialize();
            return client;
        });
    }
}
