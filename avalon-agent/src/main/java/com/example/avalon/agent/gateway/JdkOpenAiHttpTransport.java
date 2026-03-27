package com.example.avalon.agent.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Component
public class JdkOpenAiHttpTransport implements OpenAiHttpTransport {
    private final HttpClient httpClient = HttpClient.newBuilder().build();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Override
    public JsonNode postChatCompletion(URI uri, Map<String, String> headers, String requestBody, Duration timeout) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri)
                    .timeout(timeout)
                    .header("Content-Type", "application/json");
            for (Map.Entry<String, String> header : headers.entrySet()) {
                requestBuilder.header(header.getKey(), header.getValue());
            }

            HttpResponse<String> response = httpClient.send(
                    requestBuilder.POST(HttpRequest.BodyPublishers.ofString(requestBody)).build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("OpenAI-compatible request failed with status " + response.statusCode() + ": " + response.body());
            }
            return parseJsonResponse(response);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("OpenAI-compatible HTTP transport failed", exception);
        }
    }

    private JsonNode parseJsonResponse(HttpResponse<String> response) {
        try {
            return objectMapper.readTree(response.body());
        } catch (Exception exception) {
            String contentType = response.headers().firstValue("Content-Type").orElse("unknown");
            throw new IllegalStateException(
                    "OpenAI-compatible response body was not valid JSON (status "
                            + response.statusCode()
                            + ", contentType="
                            + contentType
                            + ", bodyPreview="
                            + bodyPreview(response.body())
                            + ")",
                    exception
            );
        }
    }

    private String bodyPreview(String body) {
        if (body == null || body.isBlank()) {
            return "<empty>";
        }
        String normalized = body.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 160) {
            return normalized;
        }
        return normalized.substring(0, 160) + "...";
    }
}
