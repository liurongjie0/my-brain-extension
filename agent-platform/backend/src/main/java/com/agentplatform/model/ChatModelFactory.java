package com.agentplatform.model;

import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds (and caches) a ChatModel per model config so an agent can talk to its own
 * OpenAI-compatible endpoint (base-url + key). Cache key includes the mutable fields so an
 * edited config rebuilds. The model name itself is applied per-request via chat options.
 */
@Component
public class ChatModelFactory {

    private final ConcurrentHashMap<String, OpenAiChatModel> cache = new ConcurrentHashMap<>();

    public OpenAiChatModel forConfig(ModelConfigEntity config) {
        String key = config.getId() + "|" + config.getBaseUrl() + "|" + config.getApiKey();
        return cache.computeIfAbsent(key, k -> {
            OpenAiApi api = OpenAiApi.builder()
                    .baseUrl(config.getBaseUrl())
                    .apiKey(config.getApiKey())
                    .build();
            return OpenAiChatModel.builder().openAiApi(api).build();
        });
    }
}
