package com.example.avalon.agent.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdkOpenAiHttpTransportTest {
    @Test
    void shouldParseSuccessfulJsonResponse() throws Exception {
        try (TestServer server = new TestServer(List.of(plan(200, "application/json", "{\"ok\":true,\"provider\":\"openai\"}")))) {
            JdkOpenAiHttpTransport transport = new JdkOpenAiHttpTransport();

            JsonNode response = transport.postChatCompletion(
                    server.uri(),
                    Map.of("Authorization", "Bearer test"),
                    "{\"model\":\"openai/gpt-5.4\"}",
                    Duration.ofSeconds(2)
            );

            assertTrue(response.path("ok").asBoolean());
            assertEquals("openai", response.path("provider").asText());
            assertEquals(1, server.requestCount());
        }
    }

    @Test
    void shouldRetryRetryableStatusCodeBeforeSucceeding() throws Exception {
        try (TestServer server = new TestServer(List.of(
                plan(503, "application/json", "{\"error\":\"temporary unavailable\"}"),
                plan(200, "application/json", "{\"ok\":true}")
        ))) {
            JdkOpenAiHttpTransport transport = new JdkOpenAiHttpTransport();

            JsonNode response = transport.postChatCompletion(
                    server.uri(),
                    Map.of("Authorization", "Bearer test"),
                    "{\"model\":\"openai/gpt-5.4\"}",
                    Duration.ofSeconds(2)
            );

            assertTrue(response.path("ok").asBoolean());
            assertEquals(2, server.requestCount());
        }
    }

    @Test
    void shouldNotRetryClientErrors() throws Exception {
        try (TestServer server = new TestServer(List.of(
                plan(400, "application/json", "{\"error\":\"bad request\"}"),
                plan(200, "application/json", "{\"ok\":true}")
        ))) {
            JdkOpenAiHttpTransport transport = new JdkOpenAiHttpTransport();

            OpenAiCompatibleTransportException error = assertInstanceOf(
                    OpenAiCompatibleTransportException.class,
                    assertThrows(
                            RuntimeException.class,
                            () -> transport.postChatCompletion(
                                    server.uri(),
                                    Map.of("Authorization", "Bearer test"),
                                    "{\"model\":\"openai/gpt-5.4\"}",
                                    Duration.ofSeconds(2)
                            )
                    )
            );

            assertTrue(error.getMessage().contains("status 400"));
            assertEquals(1, server.requestCount());
            assertEquals("http_status", error.diagnostics().get("failureKind"));
            assertEquals(400, error.diagnostics().get("statusCode"));
        }
    }

    @Test
    void shouldSurfaceTimeoutDiagnosticsAfterRetriesExhausted() throws Exception {
        try (TestServer server = new TestServer(List.of(
                delayedPlan(200, "application/json", "{\"ok\":true}", Duration.ofMillis(200)),
                delayedPlan(200, "application/json", "{\"ok\":true}", Duration.ofMillis(200)),
                delayedPlan(200, "application/json", "{\"ok\":true}", Duration.ofMillis(200))
        ))) {
            JdkOpenAiHttpTransport transport = new JdkOpenAiHttpTransport();

            OpenAiCompatibleTransportException error = assertInstanceOf(
                    OpenAiCompatibleTransportException.class,
                    assertThrows(
                            RuntimeException.class,
                            () -> transport.postChatCompletion(
                                    server.uri(),
                                    Map.of("Authorization", "Bearer test"),
                                    "{\"model\":\"openai/gpt-5.4\"}",
                                    Duration.ofMillis(50)
                            )
                    )
            );

            assertEquals(3, server.requestCount());
            assertEquals("transport", error.diagnostics().get("failureDomain"));
            assertEquals("timeout", error.diagnostics().get("failureKind"));
            assertEquals(3, error.diagnostics().get("transportAttempts"));
            assertEquals(50L, error.diagnostics().get("timeoutMs"));
            assertTrue(String.valueOf(error.diagnostics().get("rootExceptionClass")).contains("HttpTimeoutException"));
        }
    }

    @Test
    void shouldExplainNonJsonSuccessBodies() throws Exception {
        try (TestServer server = new TestServer(List.of(
                plan(200, "text/html; charset=utf-8", "<!doctype html><html><body>landing</body></html>")
        ))) {
            JdkOpenAiHttpTransport transport = new JdkOpenAiHttpTransport();

            OpenAiCompatibleTransportException error = assertInstanceOf(
                    OpenAiCompatibleTransportException.class,
                    assertThrows(
                            RuntimeException.class,
                            () -> transport.postChatCompletion(
                                    server.uri(),
                                    Map.of("Authorization", "Bearer test"),
                                    "{\"model\":\"openai/gpt-5.4\"}",
                                    Duration.ofSeconds(2)
                            )
                    )
            );

            assertTrue(error.getMessage().contains("OpenAI-compatible response body was not valid JSON"));
            assertTrue(error.getMessage().contains("contentType=text/html; charset=utf-8"));
            assertTrue(error.getMessage().contains("<!doctype html>"));
            assertEquals("http_response", error.diagnostics().get("failureDomain"));
            assertEquals("non_json_success_body", error.diagnostics().get("failureKind"));
        }
    }

    private static ResponsePlan plan(int statusCode, String contentType, String responseBody) {
        return new ResponsePlan(statusCode, contentType, responseBody, Duration.ZERO);
    }

    private static ResponsePlan delayedPlan(int statusCode, String contentType, String responseBody, Duration delay) {
        return new ResponsePlan(statusCode, contentType, responseBody, delay);
    }

    private record ResponsePlan(int statusCode, String contentType, String responseBody, Duration delay) {
    }

    private static final class TestServer implements AutoCloseable {
        private final HttpServer server;
        private final ArrayDeque<ResponsePlan> plans;
        private final AtomicInteger requestCount = new AtomicInteger();

        private TestServer(List<ResponsePlan> plans) throws IOException {
            server = HttpServer.create(new InetSocketAddress(0), 0);
            this.plans = new ArrayDeque<>(plans);
            server.createContext("/chat/completions", this::respond);
            server.start();
        }

        private URI uri() {
            return URI.create("http://localhost:" + server.getAddress().getPort() + "/chat/completions");
        }

        private int requestCount() {
            return requestCount.get();
        }

        @Override
        public void close() {
            server.stop(0);
        }

        private void respond(HttpExchange exchange) throws IOException {
            requestCount.incrementAndGet();
            ResponsePlan plan = plans.isEmpty() ? plan(200, "application/json", "{\"ok\":true}") : plans.removeFirst();
            try {
                if (!plan.delay().isZero()) {
                    Thread.sleep(plan.delay().toMillis());
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while delaying test response", exception);
            }
            byte[] bytes = plan.responseBody().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", plan.contentType());
            exchange.sendResponseHeaders(plan.statusCode(), bytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(bytes);
            }
        }
    }
}
