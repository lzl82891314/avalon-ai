package com.example.avalon.app.config;

import com.example.avalon.agent.gateway.ModelProfileSecretSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileBackedModelProfileApiKeyResolverTest {
    @TempDir
    Path tempDir;

    @Test
    void shouldPreferInlineApiKeyBeforeExternalSources() throws Exception {
        Path secretsFile = tempDir.resolve("avalon-model-profile-secrets.yml");
        Files.writeString(secretsFile, """
                modelProfileApiKeys:
                  gpt-5.4: from-file
                """);
        FileBackedModelProfileApiKeyResolver resolver = new FileBackedModelProfileApiKeyResolver(
                secretsFile,
                key -> "from-property",
                key -> "from-env"
        );

        String apiKey = resolver.resolveApiKey("gpt-5.4", Map.of("apiKey", "inline-key"));

        assertEquals("inline-key", apiKey);
    }

    @Test
    void shouldResolveFromSecretsFileBeforePropertyAndEnvironment() throws Exception {
        Path secretsFile = tempDir.resolve("avalon-model-profile-secrets.yml");
        Files.writeString(secretsFile, """
                modelProfileApiKeys:
                  gpt-5.4: from-file
                """);
        FileBackedModelProfileApiKeyResolver resolver = new FileBackedModelProfileApiKeyResolver(
                secretsFile,
                key -> "from-property",
                key -> "from-env"
        );

        String apiKey = resolver.resolveApiKey("gpt-5.4", Map.of());

        assertEquals("from-file", apiKey);
    }

    @Test
    void shouldFallbackToDedicatedPropertyAndEnvironmentThenLegacyEnvironmentSources() {
        FileBackedModelProfileApiKeyResolver propertyResolver = new FileBackedModelProfileApiKeyResolver(
                tempDir.resolve("missing.yml"),
                key -> ModelProfileSecretSupport.dedicatedPropertyName("gpt-5.4").equals(key) ? "from-property" : null,
                key -> null
        );
        FileBackedModelProfileApiKeyResolver dedicatedEnvResolver = new FileBackedModelProfileApiKeyResolver(
                tempDir.resolve("missing.yml"),
                key -> null,
                key -> ModelProfileSecretSupport.dedicatedEnvironmentVariableName("gpt-5.4").equals(key) ? "from-dedicated-env" : null
        );
        FileBackedModelProfileApiKeyResolver apiKeyEnvResolver = new FileBackedModelProfileApiKeyResolver(
                tempDir.resolve("missing.yml"),
                key -> null,
                key -> "OPENROUTER_API_KEY".equals(key) ? "from-api-key-env" : null
        );
        FileBackedModelProfileApiKeyResolver sharedEnvResolver = new FileBackedModelProfileApiKeyResolver(
                tempDir.resolve("missing.yml"),
                key -> null,
                key -> ModelProfileSecretSupport.DEFAULT_SHARED_ENV_VAR.equals(key) ? "from-openai-env" : null
        );

        assertEquals("from-property", propertyResolver.resolveApiKey("gpt-5.4", Map.of()));
        assertEquals("from-dedicated-env", dedicatedEnvResolver.resolveApiKey("gpt-5.4", Map.of()));
        assertEquals("from-api-key-env", apiKeyEnvResolver.resolveApiKey("gpt-5.4", Map.of("apiKeyEnv", "OPENROUTER_API_KEY")));
        assertEquals("from-openai-env", sharedEnvResolver.resolveApiKey("gpt-5.4", Map.of()));
    }

    @Test
    void shouldSupportDedicatedEnvironmentVariablesForModelIdsWithDotsAndHyphens() {
        FileBackedModelProfileApiKeyResolver resolver = new FileBackedModelProfileApiKeyResolver(
                tempDir.resolve("missing.yml"),
                key -> null,
                key -> ModelProfileSecretSupport.dedicatedEnvironmentVariableName("minimax-m2.7").equals(key) ? "from-minimax-env" : null
        );

        assertEquals("avalon.model-profile-api-keys.minimax-m2.7",
                ModelProfileSecretSupport.dedicatedPropertyName("minimax-m2.7"));
        assertEquals("AVALON_MODEL_PROFILE_API_KEY_MINIMAX_M2_7",
                ModelProfileSecretSupport.dedicatedEnvironmentVariableName("minimax-m2.7"));
        assertEquals("from-minimax-env", resolver.resolveApiKey("minimax-m2.7", Map.of()));
    }

    @Test
    void shouldReturnNullWhenNoCredentialSourceExists() {
        FileBackedModelProfileApiKeyResolver resolver = new FileBackedModelProfileApiKeyResolver(
                tempDir.resolve("missing.yml"),
                key -> null,
                key -> null
        );

        assertNull(resolver.resolveApiKey("gpt-5.4", Map.of()));
    }

    @Test
    void shouldRejectInvalidSecretsFileShape() throws Exception {
        Path secretsFile = tempDir.resolve("avalon-model-profile-secrets.yml");
        Files.writeString(secretsFile, """
                wrongRoot:
                  gpt-5.4: from-file
                """);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                new FileBackedModelProfileApiKeyResolver(secretsFile, key -> null, key -> null)
        );

        assertEquals(true, exception.getMessage().contains("modelProfileApiKeys"));
    }
}
