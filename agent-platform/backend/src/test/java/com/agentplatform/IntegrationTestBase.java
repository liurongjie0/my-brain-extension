package com.agentplatform;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

/**
 * Base for integration tests.
 *
 * <p>Uses the Testcontainers singleton-container pattern: a single MySQL container
 * is started once in a static initializer and shared across all test classes. This
 * keeps the JDBC URL stable for the JVM lifetime, so Spring's cached application
 * context stays valid across test classes (per-class containers would be stopped in
 * afterAll while a cached context still points at them). Ryuk reaps the container at
 * JVM exit.
 */
@SpringBootTest
public abstract class IntegrationTestBase {

    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.0").withDatabaseName("agent_platform");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        // 测试不打真实模型网络，但需要非空 key 让自动配置创建 bean
        registry.add("spring.ai.openai.api-key", () -> "test-key");
    }
}
