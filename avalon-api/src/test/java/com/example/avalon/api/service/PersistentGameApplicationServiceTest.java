package com.example.avalon.api.service;

import com.example.avalon.api.dto.CreateGameRequest;
import com.example.avalon.config.model.AvalonConfigRegistry;
import com.example.avalon.core.game.enums.Camp;
import com.example.avalon.core.role.model.RoleDefinition;
import com.example.avalon.core.setup.model.AssassinationRuleDefinition;
import com.example.avalon.core.setup.model.RuleSetDefinition;
import com.example.avalon.core.setup.model.SetupTemplate;
import com.example.avalon.core.setup.model.VisibilityPolicyDefinition;
import com.example.avalon.runtime.model.GameRuntimeState;
import com.example.avalon.runtime.model.GameSetup;
import com.example.avalon.runtime.orchestrator.GameOrchestrator;
import com.example.avalon.runtime.persistence.RuntimePersistenceService;
import com.example.avalon.runtime.persistence.RuntimeStateCodec;
import com.example.avalon.runtime.recovery.RecoveryService;
import com.example.avalon.runtime.recovery.ReplayQueryService;
import com.example.avalon.runtime.service.GameSessionService;
import com.example.avalon.runtime.service.TurnContextBuilder;
import com.example.avalon.runtime.engine.VisibilityService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class PersistentGameApplicationServiceTest {
    @Test
    void createGameShouldGenerateSeedWhenRequestOmitsSeed() {
        AvalonConfigRegistry configRegistry = testConfigRegistry();
        CapturingGameOrchestrator gameOrchestrator = new CapturingGameOrchestrator();
        CapturingRuntimePersistenceService persistenceService = new CapturingRuntimePersistenceService();
        SeedGenerator seedGenerator = () -> 987654321L;

        PersistentGameApplicationService service = new PersistentGameApplicationService(
                configRegistry,
                gameOrchestrator,
                new GameSessionService(),
                persistenceService,
                new RecoveryService(null, null, null, new RuntimeStateCodec()),
                new ReplayQueryService(null, null),
                new TurnContextBuilder(new VisibilityService()),
                new ModelProfileCatalogService(configRegistry, null),
                seedGenerator
        );

        service.createGame(requestWithoutSeed());

        assertNotNull(gameOrchestrator.capturedSetup);
        assertEquals(987654321L, gameOrchestrator.capturedSetup.seed());
        assertSame(gameOrchestrator.createdState, persistenceService.persistedState);
    }

    @Test
    void createGameShouldKeepExplicitSeedAndSkipGenerator() {
        AvalonConfigRegistry configRegistry = testConfigRegistry();
        CapturingGameOrchestrator gameOrchestrator = new CapturingGameOrchestrator();
        CapturingRuntimePersistenceService persistenceService = new CapturingRuntimePersistenceService();
        CountingSeedGenerator seedGenerator = new CountingSeedGenerator(987654321L);

        PersistentGameApplicationService service = new PersistentGameApplicationService(
                configRegistry,
                gameOrchestrator,
                new GameSessionService(),
                persistenceService,
                new RecoveryService(null, null, null, new RuntimeStateCodec()),
                new ReplayQueryService(null, null),
                new TurnContextBuilder(new VisibilityService()),
                new ModelProfileCatalogService(configRegistry, null),
                seedGenerator
        );

        CreateGameRequest request = requestWithoutSeed();
        request.setSeed(42L);
        service.createGame(request);

        assertNotNull(gameOrchestrator.capturedSetup);
        assertEquals(42L, gameOrchestrator.capturedSetup.seed());
        assertEquals(0, seedGenerator.callCount);
        assertSame(gameOrchestrator.createdState, persistenceService.persistedState);
    }

    private AvalonConfigRegistry testConfigRegistry() {
        SetupTemplate template = new SetupTemplate("classic-test", 1, true, List.of("MERLIN"));
        RuleSetDefinition ruleSet = new RuleSetDefinition(
                "avalon-test",
                "Avalon Test",
                "1.0.0",
                1,
                1,
                List.of(),
                Map.of(),
                List.of("classic-test"),
                new AssassinationRuleDefinition(false, "ASSASSIN", "MERLIN"),
                new VisibilityPolicyDefinition(true),
                true
        );
        RoleDefinition merlin = new RoleDefinition("MERLIN", "Merlin", Camp.GOOD, "desc", List.of(), List.of(), true, true, true, false, List.of());
        return new AvalonConfigRegistry(
                Map.of(ruleSet.ruleSetId(), ruleSet),
                Map.of(merlin.roleId(), merlin),
                Map.of(template.templateId(), template),
                Map.of()
        );
    }

    private CreateGameRequest requestWithoutSeed() {
        CreateGameRequest request = new CreateGameRequest();
        request.setRuleSetId("avalon-test");
        request.setSetupTemplateId("classic-test");

        CreateGameRequest.PlayerSlotRequest player = new CreateGameRequest.PlayerSlotRequest();
        player.setSeatNo(1);
        player.setDisplayName("P1");
        player.setControllerType("SCRIPTED");
        request.setPlayers(List.of(player));
        return request;
    }

    private static final class CapturingGameOrchestrator extends GameOrchestrator {
        private GameSetup capturedSetup;
        private GameRuntimeState createdState;

        @Override
        public GameRuntimeState createGame(GameSetup setup) {
            this.capturedSetup = setup;
            this.createdState = new GameRuntimeState(setup);
            return createdState;
        }
    }

    private static final class CapturingRuntimePersistenceService extends RuntimePersistenceService {
        private GameRuntimeState persistedState;

        private CapturingRuntimePersistenceService() {
            super(null, null, null, new RuntimeStateCodec());
        }

        @Override
        public void persist(GameRuntimeState state) {
            this.persistedState = state;
        }
    }

    private static final class CountingSeedGenerator implements SeedGenerator {
        private final long value;
        private int callCount;

        private CountingSeedGenerator(long value) {
            this.value = value;
        }

        @Override
        public long nextSeed() {
            callCount++;
            return value;
        }
    }
}
