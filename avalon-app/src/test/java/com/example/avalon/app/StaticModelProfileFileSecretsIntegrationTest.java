package com.example.avalon.app;

import com.example.avalon.agent.gateway.OpenAiHttpTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = AvalonApplication.class)
@AutoConfigureMockMvc
class StaticModelProfileFileSecretsIntegrationTest {
    private static final Path SECRETS_FILE = createSecretsFile();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OpenAiHttpTransport transport;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("avalon.model-profile-secrets.path", () -> SECRETS_FILE.toString());
    }

    @Test
    void staticProfileProbeShouldUseSecretsFileApiKey() throws Exception {
        when(transport.postChatCompletion(any(), anyMap(), anyString(), any(Duration.class)))
                .thenReturn(connectivityResponse())
                .thenReturn(structuredResponse());

        mockMvc.perform(post("/model-profiles/{modelId}/probe", "minimax-m2.7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelId").value("minimax-m2.7"))
                .andExpect(jsonPath("$.reachable").value(true))
                .andExpect(jsonPath("$.structuredCompatible").value(true));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
        verify(transport, times(2)).postChatCompletion(any(), headersCaptor.capture(), anyString(), any(Duration.class));
        assertThat(headersCaptor.getAllValues())
                .allSatisfy(headers -> assertThat(headers.get("Authorization")).isEqualTo("Bearer file-minimax-key"));
    }

    private static Path createSecretsFile() {
        try {
            Path directory = Files.createTempDirectory("avalon-model-profile-secrets");
            Path secretsFile = directory.resolve("avalon-model-profile-secrets.yml");
            Files.writeString(secretsFile, """
                    modelProfileApiKeys:
                      minimax-m2.7: file-minimax-key
                    """);
            return secretsFile;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private ObjectNode connectivityResponse() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("id", "chatcmpl-connectivity");
        root.put("model", "minimax-m2.7");
        root.putObject("usage")
                .put("prompt_tokens", 12)
                .put("completion_tokens", 4);
        root.putArray("choices")
                .addObject()
                .put("finish_reason", "stop")
                .putObject("message")
                .put("content", "hello");
        return root;
    }

    private ObjectNode structuredResponse() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("id", "chatcmpl-structured");
        root.put("model", "minimax-m2.7");
        root.putObject("usage")
                .put("prompt_tokens", 20)
                .put("completion_tokens", 10);
        root.putArray("choices")
                .addObject()
                .put("finish_reason", "stop")
                .putObject("message")
                .put("content", "{\"action\":{\"actionType\":\"PUBLIC_SPEECH\",\"speechText\":\"test\"},\"publicSpeech\":\"test\"}");
        return root;
    }
}
