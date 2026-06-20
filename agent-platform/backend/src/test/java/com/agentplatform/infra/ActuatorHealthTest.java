package com.agentplatform.infra;

import com.agentplatform.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class ActuatorHealthTest extends IntegrationTestBase {

    @Autowired MockMvc mvc;

    @Test
    void health_reports_db_and_redis_up() throws Exception {
        mvc.perform(get("/actuator/health"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.status").value("UP"))
           .andExpect(jsonPath("$.components.db.status").value("UP"))
           .andExpect(jsonPath("$.components.redis.status").value("UP"));
    }

    @Test
    void metrics_endpoint_exposed() throws Exception {
        mvc.perform(get("/actuator/metrics"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.names").isArray());
    }
}
