package com.example.avalon.app.config;

import com.example.avalon.agent.gateway.ModelProfileApiKeyResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AvalonApplicationConfigTest {
    @TempDir
    Path tempDir;

    @Test
    void shouldResolveDefaultSecretsPathFromProjectRoot() {
        AvalonApplicationConfig config = new AvalonApplicationConfig();

        Path projectRoot = ReflectionTestUtils.invokeMethod(config, "resolveProjectRoot");
        Path secretsPath = ReflectionTestUtils.invokeMethod(config, "resolveSecretsPath", "");

        assertThat(projectRoot).isNotNull();
        assertThat(Files.isRegularFile(projectRoot.resolve(".gitignore"))).isTrue();
        assertThat(Files.isDirectory(projectRoot.resolve("avalon-app"))).isTrue();
        assertThat(secretsPath).isEqualTo(projectRoot.resolve("avalon-model-profile-secrets.yml").normalize());
    }

    @Test
    void shouldLoadApiKeyFromConfiguredSecretsPath() throws IOException {
        AvalonApplicationConfig config = new AvalonApplicationConfig();
        Path secretsFile = tempDir.resolve("custom-secrets.yml");
        Files.writeString(secretsFile, """
                modelProfileApiKeys:
                  minimax-m2.7: from-file
                """);

        ModelProfileApiKeyResolver resolver = config.modelProfileApiKeyResolver(
                new MockEnvironment(),
                secretsFile.toString()
        );

        assertThat(resolver.resolveApiKey("minimax-m2.7", Map.of())).isEqualTo("from-file");
    }
}
