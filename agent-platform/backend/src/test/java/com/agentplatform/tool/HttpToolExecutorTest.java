package com.agentplatform.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class HttpToolExecutorTest {

    HttpServer server;
    int port;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/echo", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            String resp = "{\"received\":" + new String(body, StandardCharsets.UTF_8) + "}";
            byte[] out = resp.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() { server.stop(0); }

    @Test
    void posts_args_as_json_body() {
        HttpToolExecutor executor = new HttpToolExecutor(new ObjectMapper(), true);
        ToolEntity tool = new ToolEntity();
        tool.setMethod("POST");
        tool.setUrl("http://localhost:" + port + "/echo");
        String result = executor.execute(tool, "{\"city\":\"上海\"}");
        assertThat(result).contains("received").contains("上海");
    }

    @Test
    void blocks_internal_address_when_private_network_disallowed() {
        HttpToolExecutor executor = new HttpToolExecutor(new ObjectMapper(), false);
        ToolEntity tool = new ToolEntity();
        tool.setMethod("POST");
        tool.setUrl("http://localhost:" + port + "/echo");
        String result = executor.execute(tool, "{}");
        assertThat(result).contains("error").contains("blocked");
    }

    @Test
    void blocks_cloud_metadata_address() {
        HttpToolExecutor executor = new HttpToolExecutor(new ObjectMapper(), false);
        ToolEntity tool = new ToolEntity();
        tool.setMethod("GET");
        tool.setUrl("http://169.254.169.254/latest/meta-data/");
        String result = executor.execute(tool, "{}");
        assertThat(result).contains("blocked");
    }
}
