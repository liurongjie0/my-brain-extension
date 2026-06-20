package com.agentplatform;

import com.agentplatform.support.TestEmbeddingConfig;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;

/**
 * Base for integration tests.
 *
 * <p>Singleton-container pattern: one MySQL and one redis-stack container are started
 * once in a static initializer and shared across all test classes, keeping connection
 * URLs stable for the JVM lifetime so Spring's cached application context stays valid.
 * Ryuk reaps the containers at JVM exit.
 *
 * <p>Imports {@link TestEmbeddingConfig} so the (network-calling) OpenAI embedding model
 * is replaced by a deterministic fake — the Redis vector store calls embeddingModel
 * during schema init, which must not hit the network.
 */
@SpringBootTest
@Import(TestEmbeddingConfig.class)
public abstract class IntegrationTestBase {

    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.0").withDatabaseName("agent_platform");

    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis/redis-stack:latest").withExposedPorts(6379);

    static {
        MYSQL.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        // 测试不打真实模型网络，但需要非空 key 让自动配置创建 bean
        registry.add("spring.ai.openai.api-key", () -> "test-key");
        // 测试中关闭上传后自动处理，由测试显式调用 process 保证确定性
        registry.add("rag.auto-process", () -> "false");
        // 测试里的工具指向本机端口，放开内网访问
        registry.add("tool.allow-private-network", () -> "true");
    }
}
