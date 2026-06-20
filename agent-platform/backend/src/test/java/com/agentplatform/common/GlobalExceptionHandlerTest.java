package com.agentplatform.common;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void business_exception_maps_to_its_code_and_message() {
        ApiResponse<Void> r = handler.handleBusiness(new BusinessException(40001, "bad agent"));
        assertThat(r.code()).isEqualTo(40001);
        assertThat(r.message()).isEqualTo("bad agent");
    }

    @Test
    void generic_exception_maps_to_sanitized_500() {
        ApiResponse<Void> r = handler.handle(new RuntimeException("boom with sql details"));
        assertThat(r.code()).isEqualTo(500);
        // internal detail must not leak
        assertThat(r.message()).doesNotContain("sql");
        assertThat(r.message()).isEqualTo("服务器内部错误，请稍后重试");
    }
}
