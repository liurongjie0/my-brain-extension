package com.agentplatform.common;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    void ok_wraps_data_with_zero_code() {
        ApiResponse<String> r = ApiResponse.ok("hello");
        assertThat(r.code()).isEqualTo(0);
        assertThat(r.message()).isEqualTo("ok");
        assertThat(r.data()).isEqualTo("hello");
    }

    @Test
    void error_carries_code_and_message_with_null_data() {
        ApiResponse<Void> r = ApiResponse.error(404, "not found");
        assertThat(r.code()).isEqualTo(404);
        assertThat(r.message()).isEqualTo("not found");
        assertThat(r.data()).isNull();
    }
}
