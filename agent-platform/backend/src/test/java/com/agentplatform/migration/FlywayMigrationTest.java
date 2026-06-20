package com.agentplatform.migration;

import com.agentplatform.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationTest extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void all_core_tables_exist_after_migration() {
        for (String table : new String[]{
                "agent", "agent_knowledge_base", "agent_tool",
                "knowledge_base", "document", "tool",
                "conversation", "message"}) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables " +
                    "WHERE table_schema = DATABASE() AND table_name = ?",
                    Integer.class, table);
            assertThat(count).as("table %s should exist", table).isEqualTo(1);
        }
    }
}
