package com.example.avalon.config;

import com.example.avalon.config.io.YamlConfigLoader;
import com.example.avalon.config.model.AvalonConfigRegistry;
import com.example.avalon.config.service.SetupValidationService;
import com.example.avalon.core.role.model.RoleDefinition;
import com.example.avalon.core.setup.model.RuleSetDefinition;
import com.example.avalon.core.setup.model.SetupTemplate;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlConfigLoaderTest {
    @Test
    void loadsClassicFivePlayerConfigsFromApplicationResources() {
        Path resourceRoot = Path.of("..", "avalon-app", "src", "main", "resources").normalize();
        AvalonConfigRegistry registry = new YamlConfigLoader(new SetupValidationService()).load(resourceRoot);
        var openAiProfile = registry.modelProfiles().stream()
                .filter(profile -> "openai".equals(profile.provider()))
                .filter(profile -> "openai/gpt-5.4".equals(profile.modelName()))
                .findFirst()
                .orElseThrow();

        assertEquals(1, registry.ruleSets().size());
        assertEquals(5, registry.roles().size());
        assertEquals(1, registry.setupTemplates().size());
        assertEquals(5, registry.modelProfiles().size());
        assertTrue(registry.findRuleSet("avalon-classic-5p-v1").isPresent());
        assertTrue(registry.findRole("MERLIN").isPresent());
        assertTrue(registry.findSetupTemplate("classic-5p-v1").isPresent());
        assertTrue(registry.findModelProfile(openAiProfile.modelId()).isPresent());
        assertTrue(registry.findModelProfile("claude-compatible-template").isPresent());

        RuleSetDefinition ruleSet = registry.requireRuleSet("avalon-classic-5p-v1");
        RoleDefinition merlin = registry.requireRole("MERLIN");
        SetupTemplate template = registry.requireSetupTemplate("classic-5p-v1");
        var modelProfile = registry.requireModelProfile(openAiProfile.modelId());

        assertEquals("avalon-classic-5p-v1", ruleSet.ruleSetId());
        assertEquals("MERLIN", merlin.roleId());
        assertEquals("classic-5p-v1", template.templateId());
        assertEquals("openai/gpt-5.4", modelProfile.modelName());
    }
}
