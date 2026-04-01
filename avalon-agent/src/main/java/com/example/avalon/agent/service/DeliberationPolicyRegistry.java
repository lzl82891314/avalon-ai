package com.example.avalon.agent.service;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DeliberationPolicyRegistry {
    private final Map<String, DeliberationPolicy> policiesById = new LinkedHashMap<>();

    public DeliberationPolicyRegistry(List<DeliberationPolicy> policies) {
        if (policies != null) {
            policies.forEach(this::register);
        }
    }

    public DeliberationPolicyRegistry register(DeliberationPolicy policy) {
        if (policy != null) {
            policiesById.put(policy.policyId(), policy);
        }
        return this;
    }

    public DeliberationPolicy require(String policyId) {
        DeliberationPolicy policy = policiesById.get(policyId);
        if (policy == null) {
            throw new IllegalArgumentException("Unknown agent policy: " + policyId);
        }
        return policy;
    }
}
