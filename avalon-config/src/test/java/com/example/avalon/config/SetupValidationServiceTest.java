package com.example.avalon.config;

import com.example.avalon.config.exception.ConfigValidationException;
import com.example.avalon.config.io.YamlConfigLoader;
import com.example.avalon.config.service.SetupValidationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SetupValidationServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void rejectsSetupTemplateWithUnknownRoleId() throws Exception {
        Path root = tempDir.resolve("unknown-role");
        createBaseDirectories(root);
        writeClassicRule(root, "setup-1");
        writeRole(root.resolve("roles/merlin.yml"), "MERLIN", "GOOD", false);
        writeRole(root.resolve("roles/assassin.yml"), "ASSASSIN", "EVIL", true);
        Files.writeString(root.resolve("setups/setup.yml"), """
                templateId: setup-1
                playerCount: 5
                enabled: true
                roleIds:
                  - MERLIN
                  - PERCIVAL
                  - LOYAL_SERVANT
                  - MORGANA
                  - ASSASSIN
                """);

        assertThrows(ConfigValidationException.class, () ->
                new YamlConfigLoader(new SetupValidationService()).load(root)
        );
    }

    @Test
    void rejectsRuleSetWithUnsupportedSetupTemplate() throws Exception {
        Path root = tempDir.resolve("unsupported-template");
        createBaseDirectories(root);
        writeClassicRule(root, "setup-supported");
        writeRole(root.resolve("roles/merlin.yml"), "MERLIN", "GOOD", false);
        writeRole(root.resolve("roles/assassin.yml"), "ASSASSIN", "EVIL", true);
        Files.writeString(root.resolve("setups/setup.yml"), """
                templateId: setup-unsupported
                playerCount: 5
                enabled: true
                roleIds:
                  - MERLIN
                  - PERCIVAL
                  - LOYAL_SERVANT
                  - MORGANA
                  - ASSASSIN
                """);

        assertThrows(ConfigValidationException.class, () ->
                new YamlConfigLoader(new SetupValidationService()).load(root)
        );
    }

    @Test
    void allowsSetupTemplateWithRepeatedBaseRoleIds() throws Exception {
        Path root = tempDir.resolve("duplicate-base-role");
        createBaseDirectories(root);
        writeSixPlayerRule(root, "setup-6p");
        writeRole(root.resolve("roles/merlin.yml"), "MERLIN", "GOOD", false);
        writeRole(root.resolve("roles/percival.yml"), "PERCIVAL", "GOOD", false);
        writeRole(root.resolve("roles/loyal-servant.yml"), "LOYAL_SERVANT", "GOOD", false);
        writeRole(root.resolve("roles/morgana.yml"), "MORGANA", "EVIL", false);
        writeRole(root.resolve("roles/assassin.yml"), "ASSASSIN", "EVIL", true);
        Files.writeString(root.resolve("setups/setup.yml"), """
                templateId: setup-6p
                playerCount: 6
                enabled: true
                roleIds:
                  - MERLIN
                  - PERCIVAL
                  - LOYAL_SERVANT
                  - LOYAL_SERVANT
                  - MORGANA
                  - ASSASSIN
                """);

        assertDoesNotThrow(() -> new YamlConfigLoader(new SetupValidationService()).load(root));
    }

    @Test
    void rejectsModelProfileWhoseBaseUrlAlreadyPointsToChatCompletionsEndpoint() throws Exception {
        Path root = tempDir.resolve("invalid-model-profile-base-url");
        createBaseDirectories(root);
        writeClassicRule(root, "setup-1");
        writeRole(root.resolve("roles/merlin.yml"), "MERLIN", "GOOD", false);
        writeRole(root.resolve("roles/percival.yml"), "PERCIVAL", "GOOD", false);
        writeRole(root.resolve("roles/loyal-servant.yml"), "LOYAL_SERVANT", "GOOD", false);
        writeRole(root.resolve("roles/morgana.yml"), "MORGANA", "EVIL", false);
        writeRole(root.resolve("roles/assassin.yml"), "ASSASSIN", "EVIL", true);
        Files.writeString(root.resolve("setups/setup.yml"), """
                templateId: setup-1
                playerCount: 5
                enabled: true
                roleIds:
                  - MERLIN
                  - PERCIVAL
                  - LOYAL_SERVANT
                  - MORGANA
                  - ASSASSIN
                """);
        Files.writeString(root.resolve("model-profiles/openai.yml"), """
                modelId: openai-gpt-5.4
                displayName: OpenAI GPT-5.4
                provider: openai
                modelName: openai/gpt-5.4
                temperature: 0.2
                maxTokens: 320
                enabled: true
                providerOptions:
                  apiKeyEnv: OPENROUTER_API_KEY
                  baseUrl: https://openrouter.ai/api/v1/chat/completions
                """);

        assertThrows(ConfigValidationException.class, () ->
                new YamlConfigLoader(new SetupValidationService()).load(root)
        );
    }

    @Test
    void rejectsModelProfileThatStoresInlineApiKey() throws Exception {
        Path root = tempDir.resolve("inline-model-profile-api-key");
        createBaseDirectories(root);
        writeClassicRule(root, "setup-1");
        writeRole(root.resolve("roles/merlin.yml"), "MERLIN", "GOOD", false);
        writeRole(root.resolve("roles/percival.yml"), "PERCIVAL", "GOOD", false);
        writeRole(root.resolve("roles/loyal-servant.yml"), "LOYAL_SERVANT", "GOOD", false);
        writeRole(root.resolve("roles/morgana.yml"), "MORGANA", "EVIL", false);
        writeRole(root.resolve("roles/assassin.yml"), "ASSASSIN", "EVIL", true);
        Files.writeString(root.resolve("setups/setup.yml"), """
                templateId: setup-1
                playerCount: 5
                enabled: true
                roleIds:
                  - MERLIN
                  - PERCIVAL
                  - LOYAL_SERVANT
                  - MORGANA
                  - ASSASSIN
                """);
        Files.writeString(root.resolve("model-profiles/openai.yml"), """
                modelId: openai-gpt-5.4
                displayName: OpenAI GPT-5.4
                provider: openai
                modelName: openai/gpt-5.4
                temperature: 0.2
                maxTokens: 320
                enabled: true
                providerOptions:
                  apiKey: sk-test-inline
                  baseUrl: https://openrouter.ai/api/v1
                """);

        ConfigValidationException exception = assertThrows(ConfigValidationException.class, () ->
                new YamlConfigLoader(new SetupValidationService()).load(root)
        );

        org.junit.jupiter.api.Assertions.assertTrue(exception.getMessage().contains("providerOptions.apiKey"));
    }

    private static void createBaseDirectories(Path root) throws Exception {
        Files.createDirectories(root.resolve("rules"));
        Files.createDirectories(root.resolve("roles"));
        Files.createDirectories(root.resolve("setups"));
        Files.createDirectories(root.resolve("model-profiles"));
    }

    private static void writeClassicRule(Path root, String supportedTemplateId) throws Exception {
        Files.writeString(root.resolve("rules/rule.yml"), """
                ruleSetId: rule-1
                name: Test Rule
                version: 1.0.0
                minPlayers: 5
                maxPlayers: 5
                supportedSetupTemplateIds:
                  - %s
                teamSizeRules:
                  - round: 1
                    teamSize: 2
                  - round: 2
                    teamSize: 3
                  - round: 3
                    teamSize: 2
                  - round: 4
                    teamSize: 3
                  - round: 5
                    teamSize: 3
                votingRule:
                  maxRejectedTeamVotesBeforeAutoLoss: 5
                missionRule:
                  failThresholdByRound:
                    "1": 1
                    "2": 1
                    "3": 1
                    "4": 1
                    "5": 1
                assassinationRule:
                  enabled: true
                  assassinRoleId: ASSASSIN
                  merlinRoleId: MERLIN
                visibilityPolicy:
                  useRoleKnowledgeRules: true
                setupRule:
                  randomAssignment: true
                """.formatted(supportedTemplateId));
    }

    private static void writeSixPlayerRule(Path root, String supportedTemplateId) throws Exception {
        Files.writeString(root.resolve("rules/rule.yml"), """
                ruleSetId: rule-6
                name: Test Rule 6P
                version: 1.0.0
                minPlayers: 6
                maxPlayers: 6
                supportedSetupTemplateIds:
                  - %s
                teamSizeRules:
                  - round: 1
                    teamSize: 2
                  - round: 2
                    teamSize: 3
                  - round: 3
                    teamSize: 4
                  - round: 4
                    teamSize: 3
                  - round: 5
                    teamSize: 4
                votingRule:
                  maxRejectedTeamVotesBeforeAutoLoss: 5
                missionRule:
                  failThresholdByRound:
                    "1": 1
                    "2": 1
                    "3": 1
                    "4": 1
                    "5": 1
                assassinationRule:
                  enabled: true
                  assassinRoleId: ASSASSIN
                  merlinRoleId: MERLIN
                visibilityPolicy:
                  useRoleKnowledgeRules: true
                setupRule:
                  randomAssignment: true
                """.formatted(supportedTemplateId));
    }

    private static void writeRole(Path file, String roleId, String camp, boolean assassin) throws Exception {
        Files.writeString(file, """
                roleId: %s
                displayName: %s
                camp: %s
                description: Example role
                canJoinMission: true
                canVote: true
                canLead: true
                canAssassinate: %s
                knowledgeRules: []
                actionCapabilities: %s
                passiveTraits: []
                """.formatted(
                roleId,
                roleId,
                camp,
                assassin ? "true" : "false",
                assassin ? "[ASSASSINATE]" : "[]"
        ));
    }
}
