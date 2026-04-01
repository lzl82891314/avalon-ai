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
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    void createGameShouldAllowSeatBindingByLlmSeatNo() {
        AvalonConfigRegistry configRegistry = sixPlayerConfigRegistry();
        CapturingGameOrchestrator gameOrchestrator = new CapturingGameOrchestrator();
        CapturingRuntimePersistenceService persistenceService = new CapturingRuntimePersistenceService();

        PersistentGameApplicationService service = new PersistentGameApplicationService(
                configRegistry,
                gameOrchestrator,
                new GameSessionService(),
                persistenceService,
                new RecoveryService(null, null, null, new RuntimeStateCodec()),
                new ReplayQueryService(null, null),
                new TurnContextBuilder(new VisibilityService()),
                new ModelProfileCatalogService(configRegistry, emptyModelProfileStore()),
                () -> 123L
        );

        CreateGameRequest request = new CreateGameRequest();
        request.setRuleSetId("avalon-classic-6p-v2");
        request.setSetupTemplateId("classic-6p-v2");
        request.setPlayers(List.of(
                player(1), player(2), player(3), player(4), player(5), player(6)
        ));
        CreateGameRequest.LlmSelectionRequest llmSelection = new CreateGameRequest.LlmSelectionRequest();
        llmSelection.setMode("SEAT_BINDING");
        llmSelection.setSeatBindings(Map.of(
                1, "model-a",
                2, "model-a",
                3, "model-a",
                4, "model-a",
                5, "model-a",
                6, "model-a"
        ));
        request.setLlmSelection(llmSelection);

        service.createGame(request);

        assertNotNull(gameOrchestrator.capturedSetup);
        assertEquals(6, gameOrchestrator.capturedSetup.players().size());
        assertEquals(6, gameOrchestrator.capturedSetup.llmSelectionConfig().seatBindings().size());
        assertEquals("model-a", gameOrchestrator.capturedSetup.llmSelectionConfig().seatBindings().get(3));
        assertSame(gameOrchestrator.createdState, persistenceService.persistedState);
    }

    @Test
    void createGameShouldRejectSeatBindingForScriptedSeat() {
        AvalonConfigRegistry configRegistry = sixPlayerConfigRegistry();
        CapturingGameOrchestrator gameOrchestrator = new CapturingGameOrchestrator();
        CapturingRuntimePersistenceService persistenceService = new CapturingRuntimePersistenceService();

        PersistentGameApplicationService service = new PersistentGameApplicationService(
                configRegistry,
                gameOrchestrator,
                new GameSessionService(),
                persistenceService,
                new RecoveryService(null, null, null, new RuntimeStateCodec()),
                new ReplayQueryService(null, null),
                new TurnContextBuilder(new VisibilityService()),
                new ModelProfileCatalogService(configRegistry, emptyModelProfileStore()),
                () -> 123L
        );

        CreateGameRequest request = new CreateGameRequest();
        request.setRuleSetId("avalon-classic-6p-v2");
        request.setSetupTemplateId("classic-6p-v2");
        List<CreateGameRequest.PlayerSlotRequest> players = List.of(
                player(1), player(2), player(3), player(4), player(5), player(6)
        );
        players.get(1).setControllerType("SCRIPTED");
        request.setPlayers(players);
        CreateGameRequest.LlmSelectionRequest llmSelection = new CreateGameRequest.LlmSelectionRequest();
        llmSelection.setMode("SEAT_BINDING");
        llmSelection.setSeatBindings(Map.of(
                1, "model-a",
                2, "model-a",
                3, "model-a",
                4, "model-a",
                5, "model-a",
                6, "model-a"
        ));
        request.setLlmSelection(llmSelection);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> service.createGame(request));

        assertEquals("SEAT_BINDING may only reference active LLM seats", exception.getMessage());
    }

    @Test
    void createGameShouldAllowRoleBindingWithRepeatedBaseRoleIds() {
        AvalonConfigRegistry configRegistry = sixPlayerConfigRegistry();
        CapturingGameOrchestrator gameOrchestrator = new CapturingGameOrchestrator();
        CapturingRuntimePersistenceService persistenceService = new CapturingRuntimePersistenceService();

        PersistentGameApplicationService service = new PersistentGameApplicationService(
                configRegistry,
                gameOrchestrator,
                new GameSessionService(),
                persistenceService,
                new RecoveryService(null, null, null, new RuntimeStateCodec()),
                new ReplayQueryService(null, null),
                new TurnContextBuilder(new VisibilityService()),
                new ModelProfileCatalogService(configRegistry, emptyModelProfileStore()),
                () -> 123L
        );

        CreateGameRequest request = new CreateGameRequest();
        request.setRuleSetId("avalon-classic-6p-v2");
        request.setSetupTemplateId("classic-6p-v2");
        request.setPlayers(List.of(
                player(1), player(2), player(3), player(4), player(5), player(6)
        ));
        CreateGameRequest.LlmSelectionRequest llmSelection = new CreateGameRequest.LlmSelectionRequest();
        llmSelection.setMode("ROLE_BINDING");
        llmSelection.setRoleBindings(Map.of(
                "MERLIN", "model-a",
                "PERCIVAL", "model-a",
                "LOYAL_SERVANT", "model-a",
                "MORGANA", "model-a",
                "ASSASSIN", "model-a"
        ));
        request.setLlmSelection(llmSelection);

        service.createGame(request);

        assertNotNull(gameOrchestrator.capturedSetup);
        assertEquals(6, gameOrchestrator.capturedSetup.players().size());
        assertEquals(5, gameOrchestrator.capturedSetup.llmSelectionConfig().roleBindings().size());
        assertEquals("model-a", gameOrchestrator.capturedSetup.llmSelectionConfig().roleBindings().get("LOYAL_SERVANT"));
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

    private AvalonConfigRegistry sixPlayerConfigRegistry() {
        SetupTemplate template = new SetupTemplate(
                "classic-6p-v2",
                6,
                true,
                List.of("MERLIN", "PERCIVAL", "LOYAL_SERVANT", "LOYAL_SERVANT", "MORGANA", "ASSASSIN")
        );
        RuleSetDefinition ruleSet = new RuleSetDefinition(
                "avalon-classic-6p-v2",
                "Avalon Classic 6 Players V2",
                "2.0.0",
                6,
                6,
                List.of(
                        new com.example.avalon.core.setup.model.RoundTeamSizeRule(1, 2),
                        new com.example.avalon.core.setup.model.RoundTeamSizeRule(2, 3),
                        new com.example.avalon.core.setup.model.RoundTeamSizeRule(3, 4),
                        new com.example.avalon.core.setup.model.RoundTeamSizeRule(4, 3),
                        new com.example.avalon.core.setup.model.RoundTeamSizeRule(5, 4)
                ),
                Map.of(1, 1, 2, 1, 3, 1, 4, 1, 5, 1),
                List.of("classic-6p-v2"),
                new AssassinationRuleDefinition(true, "ASSASSIN", "MERLIN"),
                new VisibilityPolicyDefinition(true),
                true
        );
        RoleDefinition merlin = new RoleDefinition("MERLIN", "Merlin", Camp.GOOD, "desc", List.of(), List.of(), true, true, true, false, List.of());
        RoleDefinition percival = new RoleDefinition("PERCIVAL", "Percival", Camp.GOOD, "desc", List.of(), List.of(), true, true, true, false, List.of());
        RoleDefinition loyalServant = new RoleDefinition("LOYAL_SERVANT", "Loyal Servant", Camp.GOOD, "desc", List.of(), List.of(), true, true, true, false, List.of());
        RoleDefinition morgana = new RoleDefinition("MORGANA", "Morgana", Camp.EVIL, "desc", List.of(), List.of(), true, true, true, false, List.of());
        RoleDefinition assassin = new RoleDefinition("ASSASSIN", "Assassin", Camp.EVIL, "desc", List.of(), List.of("ASSASSINATE"), true, true, true, true, List.of());
        return new AvalonConfigRegistry(
                Map.of(ruleSet.ruleSetId(), ruleSet),
                Map.of(
                        merlin.roleId(), merlin,
                        percival.roleId(), percival,
                        loyalServant.roleId(), loyalServant,
                        morgana.roleId(), morgana,
                        assassin.roleId(), assassin
                ),
                Map.of(template.templateId(), template),
                Map.of("model-a", new com.example.avalon.config.model.LlmModelDefinition(
                        "model-a",
                        "Model A",
                        "openai",
                        "model-a",
                        0.2,
                        256,
                        Map.of(),
                        true
                ))
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

    private CreateGameRequest.PlayerSlotRequest player(int seatNo) {
        CreateGameRequest.PlayerSlotRequest player = new CreateGameRequest.PlayerSlotRequest();
        player.setSeatNo(seatNo);
        player.setDisplayName("P" + seatNo);
        player.setControllerType("LLM");
        return player;
    }

    private com.example.avalon.persistence.store.ModelProfileStore emptyModelProfileStore() {
        return new com.example.avalon.persistence.store.ModelProfileStore() {
            @Override
            public com.example.avalon.persistence.model.ModelProfileRecord save(com.example.avalon.persistence.model.ModelProfileRecord record) {
                return record;
            }

            @Override
            public List<com.example.avalon.persistence.model.ModelProfileRecord> findAll() {
                return List.of();
            }

            @Override
            public java.util.Optional<com.example.avalon.persistence.model.ModelProfileRecord> findByModelId(String modelId) {
                return java.util.Optional.empty();
            }

            @Override
            public void deleteByModelId(String modelId) {
            }
        };
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
