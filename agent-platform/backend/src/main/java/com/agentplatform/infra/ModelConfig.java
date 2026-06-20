package com.agentplatform.infra;

import org.springframework.context.annotation.Configuration;

/**
 * Infra model configuration anchor.
 *
 * <p>Chat/Embedding models are provided by Spring AI's OpenAI auto-configuration
 * (spring-ai-starter-model-openai) and point to an OpenAI-compatible gateway via
 * {@code spring.ai.openai.base-url}. This class is the extension point for later
 * plans (custom retry/timeout, observation, prompt defaults).
 */
@Configuration
public class ModelConfig {
}
