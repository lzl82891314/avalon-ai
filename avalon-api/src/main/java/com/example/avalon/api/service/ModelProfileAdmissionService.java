package com.example.avalon.api.service;

import com.example.avalon.agent.gateway.OpenAiCompatibleSupport;
import com.example.avalon.agent.model.PlayerAgentConfig;
import com.example.avalon.agent.service.AgentPolicyIds;
import com.example.avalon.api.dto.CreateGameRequest;
import com.example.avalon.api.model.CatalogModelProfile;
import com.example.avalon.core.player.enums.PlayerControllerType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ModelProfileAdmissionService {
    private final ModelProfileCatalogService modelProfileCatalogService;
    private final String defaultPolicyId;

    @Autowired
    ModelProfileAdmissionService(ModelProfileCatalogService modelProfileCatalogService,
                                 @Value("${avalon.agent.default-policy-id:}") String defaultPolicyId) {
        this.modelProfileCatalogService = modelProfileCatalogService;
        this.defaultPolicyId = blankToNull(defaultPolicyId);
    }

    public void validateCreateGameRequest(CreateGameRequest request) {
        if (request == null || request.getPlayers() == null || request.getPlayers().isEmpty()) {
            return;
        }
        Map<Integer, PlayerPolicyContext> llmSeatPolicies = llmSeatPolicies(request);
        if (llmSeatPolicies.isEmpty()) {
            return;
        }
        Map<String, Set<String>> modelStages = requestedModelStages(request, llmSeatPolicies);
        for (Map.Entry<String, Set<String>> entry : modelStages.entrySet()) {
            ensureEligible(entry.getKey(), entry.getValue());
        }
    }

    public boolean isEligible(String modelId, String policyId) {
        CatalogModelProfile profile = modelProfileCatalogService.requireEnabledProfile(modelId);
        return isEligible(profile, stagesForPolicy(policyId));
    }

    private void ensureEligible(String modelId, Set<String> requiredStages) {
        CatalogModelProfile profile = modelProfileCatalogService.requireEnabledProfile(modelId);
        if (isEligible(profile, requiredStages)) {
            return;
        }
        throw new IllegalArgumentException(
                "Model profile '%s' is not admitted for stages %s. Configure providerOptions.avalonRuntime in the profile."
                        .formatted(modelId, requiredStages)
        );
    }

    private boolean isEligible(CatalogModelProfile profile, Set<String> requiredStages) {
        if (!hasExplicitAvalonRuntime(profile)) {
            return true;
        }
        if (!OpenAiCompatibleSupport.admissionEligible(profile.provider(), profile.providerOptions())) {
            return false;
        }
        if (requiredStages == null || requiredStages.isEmpty()) {
            return true;
        }
        return requiredStages.stream().allMatch(stageId ->
                OpenAiCompatibleSupport.stageBudget(profile.provider(), profile.providerOptions(), stageId) != null
        );
    }

    private boolean hasExplicitAvalonRuntime(CatalogModelProfile profile) {
        if (profile == null || profile.providerOptions() == null || profile.providerOptions().isEmpty()) {
            return false;
        }
        Object runtime = profile.providerOptions().get("avalonRuntime");
        return runtime instanceof Map<?, ?> runtimeMap && !runtimeMap.isEmpty();
    }

    private Map<Integer, PlayerPolicyContext> llmSeatPolicies(CreateGameRequest request) {
        Map<Integer, PlayerPolicyContext> policies = new LinkedHashMap<>();
        for (CreateGameRequest.PlayerSlotRequest player : request.getPlayers()) {
            PlayerControllerType controllerType = PlayerControllerType.valueOf(
                    blankToNull(player.getControllerType()) == null ? "SCRIPTED" : player.getControllerType().trim().toUpperCase(Locale.ROOT)
            );
            if (controllerType != PlayerControllerType.LLM) {
                continue;
            }
            int seatNo = player.getSeatNo() == null ? policies.size() + 1 : player.getSeatNo();
            PlayerAgentConfig config = player.getAgentConfig() == null ? new PlayerAgentConfig() : player.getAgentConfig();
            String policyId = effectivePolicyId(config);
            policies.put(seatNo, new PlayerPolicyContext(policyId, stagesForPolicy(policyId), hasExplicitCriticSlot(config)));
        }
        return policies;
    }

    private Map<String, Set<String>> requestedModelStages(CreateGameRequest request,
                                                          Map<Integer, PlayerPolicyContext> llmSeatPolicies) {
        Map<String, Set<String>> stagesByModelId = new LinkedHashMap<>();
        CreateGameRequest.LlmSelectionRequest llmSelection = request.getLlmSelection();
        if (llmSelection != null && blankToNull(llmSelection.getMode()) != null) {
            mergeSelectionStages(stagesByModelId, llmSelection, llmSeatPolicies);
        }
        mergeExplicitSlotStages(stagesByModelId, request, llmSeatPolicies);
        return stagesByModelId;
    }

    private void mergeSelectionStages(Map<String, Set<String>> stagesByModelId,
                                      CreateGameRequest.LlmSelectionRequest llmSelection,
                                      Map<Integer, PlayerPolicyContext> llmSeatPolicies) {
        String mode = llmSelection.getMode().trim().toUpperCase(Locale.ROOT);
        switch (mode) {
            case "SEAT_BINDING" -> llmSelection.getSeatBindings().forEach((seatNo, modelId) -> {
                PlayerPolicyContext context = llmSeatPolicies.get(seatNo);
                if (context != null) {
                    mergeStages(stagesByModelId, modelId, context.actorStages());
                    if (context.hasExplicitCriticSlot()) {
                        mergeStages(stagesByModelId, modelId, Set.of("critic-stage"));
                    }
                }
            });
            case "ROLE_BINDING", "RANDOM_POOL" -> {
                Set<String> unionStages = new LinkedHashSet<>();
                llmSeatPolicies.values().forEach(context -> unionStages.addAll(context.allStages()));
                if ("ROLE_BINDING".equals(mode)) {
                    llmSelection.getRoleBindings().values().forEach(modelId -> mergeStages(stagesByModelId, modelId, unionStages));
                } else {
                    llmSelection.getCandidateModelIds().forEach(modelId -> mergeStages(stagesByModelId, modelId, unionStages));
                }
            }
            default -> {
            }
        }
    }

    private void mergeExplicitSlotStages(Map<String, Set<String>> stagesByModelId,
                                         CreateGameRequest request,
                                         Map<Integer, PlayerPolicyContext> llmSeatPolicies) {
        for (CreateGameRequest.PlayerSlotRequest player : request.getPlayers()) {
            if (player.getAgentConfig() == null) {
                continue;
            }
            int seatNo = player.getSeatNo() == null ? 0 : player.getSeatNo();
            PlayerPolicyContext context = llmSeatPolicies.get(seatNo);
            if (context == null) {
                continue;
            }
            PlayerAgentConfig config = player.getAgentConfig();
            if (catalogBackedModelId(config.getModelProfile().getModelId())) {
                mergeStages(stagesByModelId, config.getModelProfile().getModelId(), context.allStages());
            }
            config.getModelSlots().forEach((slotId, profile) -> {
                if (profile == null || !catalogBackedModelId(profile.getModelId())) {
                    return;
                }
                if ("critic".equalsIgnoreCase(slotId)) {
                    mergeStages(stagesByModelId, profile.getModelId(), Set.of("critic-stage"));
                    return;
                }
                mergeStages(stagesByModelId, profile.getModelId(), context.actorStages());
            });
        }
    }

    private void mergeStages(Map<String, Set<String>> stagesByModelId, String rawModelId, Set<String> stages) {
        String modelId = blankToNull(rawModelId);
        if (modelId == null || stages == null || stages.isEmpty()) {
            return;
        }
        stagesByModelId.computeIfAbsent(modelId, ignored -> new LinkedHashSet<>()).addAll(stages);
    }

    private boolean catalogBackedModelId(String rawModelId) {
        String modelId = blankToNull(rawModelId);
        if (modelId == null) {
            return false;
        }
        try {
            modelProfileCatalogService.requireEnabledProfile(modelId);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private String effectivePolicyId(PlayerAgentConfig config) {
        String explicitPolicyId = config == null ? null : blankToNull(config.getAgentPolicyId());
        if (explicitPolicyId != null) {
            return explicitPolicyId;
        }
        if (defaultPolicyId != null) {
            return defaultPolicyId;
        }
        return AgentPolicyIds.LEGACY_SINGLE_SHOT;
    }

    private boolean hasExplicitCriticSlot(PlayerAgentConfig config) {
        if (config == null || config.getModelSlots() == null || config.getModelSlots().isEmpty()) {
            return false;
        }
        return config.getModelSlots().entrySet().stream()
                .anyMatch(entry -> "critic".equalsIgnoreCase(entry.getKey())
                        && entry.getValue() != null
                        && blankToNull(entry.getValue().getModelId()) != null);
    }

    private Set<String> stagesForPolicy(String policyId) {
        String effectivePolicyId = blankToNull(policyId);
        if (effectivePolicyId == null) {
            effectivePolicyId = AgentPolicyIds.LEGACY_SINGLE_SHOT;
        }
        return switch (effectivePolicyId) {
            case AgentPolicyIds.TOM_V1 -> Set.of("belief-stage", "decision-stage");
            case AgentPolicyIds.TOM_TOT_V1 -> Set.of("belief-stage", "tot-stage", "decision-stage");
            case AgentPolicyIds.TOM_TOT_CRITIC_V1 -> Set.of("belief-stage", "tot-stage", "critic-stage", "decision-stage");
            default -> Set.of("decision-stage");
        };
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private record PlayerPolicyContext(String policyId, Set<String> allStages, boolean hasExplicitCriticSlot) {
        Set<String> actorStages() {
            if (!hasExplicitCriticSlot) {
                return allStages;
            }
            List<String> stages = new ArrayList<>();
            for (String stage : allStages) {
                if (!"critic-stage".equals(stage)) {
                    stages.add(stage);
                }
            }
            return Set.copyOf(stages);
        }
    }
}
