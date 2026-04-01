package com.example.avalon.config;

import com.example.avalon.config.exception.ConfigLoadException;
import com.example.avalon.config.io.YamlConfigLoader;
import com.example.avalon.config.model.AvalonConfigRegistry;
import com.example.avalon.config.service.SetupValidationService;
import com.example.avalon.core.role.enums.KnowledgeRuleType;
import com.example.avalon.core.role.model.RoleDefinition;
import com.example.avalon.core.setup.model.RuleSetDefinition;
import com.example.avalon.core.setup.model.SetupTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlConfigLoaderTest {
    @Test
    void loadsClassicFiveToTenPlayerConfigsFromApplicationResources() {
        Path resourceRoot = Path.of("..", "avalon-app", "src", "main", "resources").normalize();
        AvalonConfigRegistry registry = new YamlConfigLoader(new SetupValidationService()).load(resourceRoot);
        var modelProfile = registry.modelProfiles().stream().findFirst().orElseThrow();

        assertTrue(registry.ruleSets().size() >= 7);
        assertTrue(registry.roles().size() >= 7);
        assertTrue(registry.setupTemplates().size() >= 7);
        assertEquals(5, registry.modelProfiles().size());
        assertTrue(registry.findRuleSet("avalon-classic-5p-v1").isPresent());
        assertTrue(registry.findRuleSet("avalon-classic-10p-v2").isPresent());
        assertTrue(registry.findRole("MERLIN").isPresent());
        assertTrue(registry.findRole("MORDRED").isPresent());
        assertTrue(registry.findRole("OBERON").isPresent());
        assertTrue(registry.findSetupTemplate("classic-5p-v1").isPresent());
        assertTrue(registry.findSetupTemplate("classic-10p-v2").isPresent());
        assertTrue(registry.findModelProfile(modelProfile.modelId()).isPresent());
        assertTrue(registry.findModelProfile("claude-sonnet-4-6").isPresent());

        RuleSetDefinition ruleSet = registry.requireRuleSet("avalon-classic-10p-v2");
        RoleDefinition merlin = registry.requireRole("MERLIN");
        RoleDefinition percival = registry.requireRole("PERCIVAL");
        RoleDefinition morgana = registry.requireRole("MORGANA");
        SetupTemplate template = registry.requireSetupTemplate("classic-10p-v2");
        var loadedModelProfile = registry.requireModelProfile(modelProfile.modelId());

        assertEquals("avalon-classic-10p-v2", ruleSet.ruleSetId());
        assertEquals("MERLIN", merlin.roleId());
        assertEquals("classic-10p-v2", template.templateId());
        assertEquals(10, template.playerCount());
        assertEquals(4, template.roleIds().stream().filter("LOYAL_SERVANT"::equals).count());
        assertEquals(2, ruleSet.failThresholdForRound(4));
        assertEquals(modelProfile.modelId(), loadedModelProfile.modelId());
        assertTrue(loadedModelProfile.providerOptions().containsKey("apiKey") == false);
        assertEquals(KnowledgeRuleType.SEE_ROLE_AMBIGUITY, percival.knowledgeRules().get(0).type());
        assertEquals(KnowledgeRuleType.SEE_ALLIED_EVIL_PLAYERS, morgana.knowledgeRules().get(0).type());
    }

    @Test
    void shouldRejectMisleadPercivalKnowledgeRule(@TempDir Path tempDir) throws Exception {
        Path sourceRoot = Path.of("..", "avalon-app", "src", "main", "resources").normalize();
        copyDirectory(sourceRoot, tempDir);
        Files.writeString(tempDir.resolve("roles").resolve("morgana.yml"), """
                roleId: MORGANA
                displayName: Morgana
                camp: EVIL
                description: Appears as Merlin to Percival.
                canJoinMission: true
                canVote: true
                canLead: true
                canAssassinate: false
                knowledgeRules:
                  - type: MISLEAD_PERCIVAL
                actionCapabilities: []
                passiveTraits: []
                """);

        ConfigLoadException exception = assertThrows(
                ConfigLoadException.class,
                () -> new YamlConfigLoader(new SetupValidationService()).load(tempDir)
        );

        assertTrue(exception.getMessage().contains("MISLEAD_PERCIVAL"));
    }

    private void copyDirectory(Path sourceRoot, Path targetRoot) throws IOException {
        try (var stream = Files.walk(sourceRoot)) {
            stream.sorted(Comparator.naturalOrder()).forEach(path -> copyPath(sourceRoot, targetRoot, path));
        }
    }

    private void copyPath(Path sourceRoot, Path targetRoot, Path source) {
        try {
            Path target = targetRoot.resolve(sourceRoot.relativize(source).toString());
            if (Files.isDirectory(source)) {
                Files.createDirectories(target);
                return;
            }
            Files.createDirectories(target.getParent());
            Files.copy(source, target);
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }
}
