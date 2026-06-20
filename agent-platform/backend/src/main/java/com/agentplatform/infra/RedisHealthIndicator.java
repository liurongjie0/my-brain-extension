package com.agentplatform.infra;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisPooled;

/**
 * Health indicator for the Redis Stack vector store backend. Uses the same JedisPooled
 * the vector store uses (Spring's built-in Redis indicator needs a RedisConnectionFactory,
 * which we don't create — we drive Jedis directly). Surfaces as the "redis" component
 * under /actuator/health.
 */
@Component("redis")
public class RedisHealthIndicator implements HealthIndicator {

    private final JedisPooled jedisPooled;

    public RedisHealthIndicator(JedisPooled jedisPooled) {
        this.jedisPooled = jedisPooled;
    }

    @Override
    public Health health() {
        try {
            String pong = jedisPooled.ping();
            return Health.up().withDetail("ping", pong).build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
