package com.example.avalon.runtime.engine;

import com.example.avalon.runtime.model.GameRuntimeState;

public interface GameRuleEngine {
    int teamSizeForRound(int roundNo);

    int missionFailThresholdForRound(int roundNo);

    boolean isGameOver(GameRuntimeState state);

    boolean shouldEnterAssassination(GameRuntimeState state);

    String resolveWinner(GameRuntimeState state);
}

