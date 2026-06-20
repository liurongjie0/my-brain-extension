package com.agentplatform.infra;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPooled;

import static org.springframework.ai.vectorstore.redis.RedisVectorStore.MetadataField;

@Configuration
public class VectorStoreConfig {

    @Bean
    public JedisPooled jedisPooled(@Value("${spring.data.redis.host:localhost}") String host,
                                   @Value("${spring.data.redis.port:6379}") int port) {
        return new JedisPooled(host, port);
    }

    @Bean
    public VectorStore vectorStore(JedisPooled jedisPooled, EmbeddingModel embeddingModel) {
        return RedisVectorStore.builder(jedisPooled, embeddingModel)
                .indexName("agent-kb-index")
                .prefix("kb:")
                .metadataFields(MetadataField.tag("kb_id"), MetadataField.tag("doc_id"))
                .initializeSchema(true)
                .build();
    }
}
