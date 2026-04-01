package com.example.avalon.core.player.memory;

public record PlayerBeliefState(
        double firstOrderEvilScore,
        double secondOrderAwarenessScore,
        double thirdOrderManipulationRisk,
        double confidence
) {
    public PlayerBeliefState {
        firstOrderEvilScore = clamp(firstOrderEvilScore);
        secondOrderAwarenessScore = clamp(secondOrderAwarenessScore);
        thirdOrderManipulationRisk = clamp(thirdOrderManipulationRisk);
        confidence = clamp(confidence);
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
