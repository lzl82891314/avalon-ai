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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdkOpenAiHttpTransportTest {
    @Test
    void shouldParseSuccessfulJsonResponse() throws Exception {
        try (TestServer server = new TestServer(200, "application/json", "{\"ok\":true,\"provider\":\"openai\"}")) {
            JdkOpenAiHttpTransport transport = new JdkOpenAiHttpTransport();

            JsonNode response = transport.postChatCompletion(
                    server.uri(),
                    Map.of("Authorization", "Bearer test"),
                    "{\"model\":\"openai/gpt-5.4\"}",
                    Duration.ofSeconds(2)
            );

            assertTrue(response.path("ok").asBoolean());
            assertEquals("openai", response.path("provider").asText());
        }
    }

    @Test
    void shouldExplainNonJsonSuccessBodies() throws Exception {
        try (TestServer server = new TestServer(200, "text/html; charset=utf-8", "<!doctype html><html><body>landing</body></html>")) {
            JdkOpenAiHttpTransport transport = new JdkOpenAiHttpTransport();

            IllegalStateException error = assertThrows(
                    IllegalStateException.class,
                    () -> transport.postChatCompletion(
                            server.uri(),
                            Map.of("Authorization", "Bearer test"),
                            "{\"model\":\"openai/gpt-5.4\"}",
                            Duration.ofSeconds(2)
                    )
            );

            assertTrue(error.getMessage().contains("OpenAI-compatible response body was not valid JSON"));
            assertTrue(error.getMessage().contains("contentType=text/html; charset=utf-8"));
            assertTrue(error.getMessage().contains("<!doctype html>"));
        }
    }

    private static final class TestServer implements AutoCloseable {
        private final HttpServer server;

        private TestServer(int statusCode, String contentType, String responseBody) throws IOException {
            server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/chat/completions", exchange -> respond(exchange, statusCode, contentType, responseBody));
            server.start();
        }

        private URI uri() {
            return URI.create("http://localhost:" + server.getAddress().getPort() + "/chat/completions");
        }

        @Override
        public void close() {
            server.stop(0);
        }

        private static void respond(HttpExchange exchange,
                                    int statusCode,
                                    String contentType,
                                    String responseBody) throws IOException {
            byte[] bytes = responseBody.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(bytes);
            }
        }
    }
}
