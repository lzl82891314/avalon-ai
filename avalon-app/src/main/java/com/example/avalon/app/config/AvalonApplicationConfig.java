package com.example.avalon.app.config;

import com.example.avalon.agent.controller.LlmPlayerController;
import com.example.avalon.agent.gateway.AgentGateway;
import com.example.avalon.agent.model.PlayerAgentConfig;
import com.example.avalon.agent.service.AgentTurnRequestFactory;
import com.example.avalon.agent.service.PromptBuilder;
import com.example.avalon.agent.service.ResponseParser;
import com.example.avalon.agent.service.ValidationRetryPolicy;
import com.example.avalon.api.service.LlmSelectionResolutionService;
import com.example.avalon.config.io.YamlConfigLoader;
import com.example.avalon.config.model.AvalonConfigRegistry;
import com.example.avalon.config.service.SetupValidationService;
import com.example.avalon.core.player.enums.PlayerControllerType;
import com.example.avalon.persistence.store.AuditRecordStore;
import com.example.avalon.persistence.store.GameEventStore;
import com.example.avalon.persistence.store.GameSnapshotStore;
import com.example.avalon.persistence.store.PlayerMemorySnapshotStore;
import com.example.avalon.runtime.controller.PlayerControllerResolver;
import com.example.avalon.runtime.engine.ClassicFivePlayerGameRuleEngine;
import com.example.avalon.runtime.engine.GameRuleEngine;
import com.example.avalon.runtime.engine.RoleAssignmentService;
import com.example.avalon.runtime.engine.VisibilityService;
import com.example.avalon.runtime.orchestrator.GameOrchestrator;
import com.example.avalon.runtime.persistence.RuntimePersistenceService;
import com.example.avalon.runtime.persistence.RuntimeStateCodec;
import com.example.avalon.runtime.recovery.RecoveryService;
import com.example.avalon.runtime.recovery.ReplayQueryService;
import com.example.avalon.runtime.service.GameSessionService;
import com.example.avalon.runtime.service.ResolvedLlmConfigInitializer;
import com.example.avalon.runtime.service.TurnContextBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class AvalonApplicationConfig {
    @Bean
    AvalonConfigRegistry avalonConfigRegistry() {
        YamlConfigLoader loader = new YamlConfigLoader(new SetupValidationService());
        return loader.loadAndValidate(resolveResourcesPath());
    }

    @Bean
    GameSessionService gameSessionService() {
        return new GameSessionService();
    }

    @Bean
    GameRuleEngine gameRuleEngine() {
        return new ClassicFivePlayerGameRuleEngine();
    }

    @Bean
    RoleAssignmentService roleAssignmentService() {
        return new RoleAssignmentService();
    }

    @Bean
    VisibilityService visibilityService() {
        return new VisibilityService();
    }

    @Bean
    PlayerControllerResolver playerControllerResolver(
            AgentGateway agentGateway,
            AgentTurnRequestFactory agentTurnRequestFactory,
            PromptBuilder promptBuilder,
            ResponseParser responseParser,
            ValidationRetryPolicy validationRetryPolicy
    ) {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        PlayerControllerResolver resolver = new PlayerControllerResolver();
        resolver.registerFactory(PlayerControllerType.LLM, (state, player) -> new LlmPlayerController(
                agentGateway,
                agentTurnRequestFactory,
                promptBuilder,
                responseParser,
                validationRetryPolicy,
                objectMapper.convertValue(
                        state.resolvedLlmControllerConfigOf(player.playerId()) == null
                                ? player.controllerConfig()
                                : state.resolvedLlmControllerConfigOf(player.playerId()),
                        PlayerAgentConfig.class)
        ));
        return resolver;
    }

    @Bean
    TurnContextBuilder turnContextBuilder(VisibilityService visibilityService) {
        return new TurnContextBuilder(visibilityService);
    }

    @Bean
    RuntimeStateCodec runtimeStateCodec() {
        return new RuntimeStateCodec();
    }

    @Bean
    RuntimePersistenceService runtimePersistenceService(
            GameEventStore gameEventStore,
            GameSnapshotStore gameSnapshotStore,
            PlayerMemorySnapshotStore playerMemorySnapshotStore,
            AuditRecordStore auditRecordStore,
            RuntimeStateCodec runtimeStateCodec
    ) {
        return new RuntimePersistenceService(gameEventStore, gameSnapshotStore, playerMemorySnapshotStore, auditRecordStore, runtimeStateCodec);
    }

    @Bean
    RecoveryService recoveryService(
            GameSnapshotStore gameSnapshotStore,
            GameEventStore gameEventStore,
            PlayerMemorySnapshotStore playerMemorySnapshotStore,
            RuntimeStateCodec runtimeStateCodec
    ) {
        return new RecoveryService(gameSnapshotStore, gameEventStore, playerMemorySnapshotStore, runtimeStateCodec);
    }

    @Bean
    ReplayQueryService replayQueryService(GameEventStore gameEventStore, AuditRecordStore auditRecordStore) {
        return new ReplayQueryService(gameEventStore, auditRecordStore);
    }

    @Bean
    GameOrchestrator gameOrchestrator(
            GameSessionService gameSessionService,
            GameRuleEngine gameRuleEngine,
            RoleAssignmentService roleAssignmentService,
            VisibilityService visibilityService,
            PlayerControllerResolver playerControllerResolver,
            LlmSelectionResolutionService llmSelectionResolutionService
    ) {
        return new GameOrchestrator(
                gameSessionService,
                gameRuleEngine,
                roleAssignmentService,
                visibilityService,
                playerControllerResolver,
                llmSelectionResolutionService
        );
    }

    private Path resolveResourcesPath() {
        Path[] candidates = new Path[] {
                Path.of("avalon-app", "src", "main", "resources"),
                Path.of("src", "main", "resources")
        };
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to locate avalon-app resources directory");
    }
}
