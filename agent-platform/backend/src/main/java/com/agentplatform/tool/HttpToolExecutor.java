package com.agentplatform.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Component
public class HttpToolExecutor {

    private final ObjectMapper objectMapper;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    public HttpToolExecutor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String execute(ToolEntity tool, String argsJson) {
        try {
            String method = tool.getMethod() != null ? tool.getMethod().toUpperCase() : "POST";
            String url = tool.getUrl();
            HttpRequest.Builder builder = HttpRequest.newBuilder().timeout(Duration.ofSeconds(30));

            applyHeaders(builder, tool.getHeadersJson());

            if ("GET".equals(method)) {
                url = url + toQuery(argsJson);
                builder.uri(URI.create(url)).GET();
            } else {
                builder.uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(
                                argsJson != null ? argsJson : "{}", StandardCharsets.UTF_8));
            }

            HttpResponse<String> resp = client.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String body = resp.body();
            return body.length() > 8192 ? body.substring(0, 8192) : body;
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    private void applyHeaders(HttpRequest.Builder builder, String headersJson) {
        if (headersJson == null || headersJson.isBlank()) return;
        try {
            Map<String, String> headers = objectMapper.readValue(headersJson, new TypeReference<>() {});
            headers.forEach(builder::header);
        } catch (Exception ignored) {
        }
    }

    private String toQuery(String argsJson) {
        if (argsJson == null || argsJson.isBlank()) return "";
        try {
            Map<String, Object> args = objectMapper.readValue(argsJson, new TypeReference<>() {});
            if (args.isEmpty()) return "";
            StringBuilder sb = new StringBuilder("?");
            args.forEach((k, v) -> sb.append(URLEncoder.encode(k, StandardCharsets.UTF_8))
                    .append("=").append(URLEncoder.encode(String.valueOf(v), StandardCharsets.UTF_8)).append("&"));
            return sb.substring(0, sb.length() - 1);
        } catch (Exception e) {
            return "";
        }
    }
}
