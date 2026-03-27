package com.example.avalon.runtime.engine;

import com.example.avalon.core.game.enums.GameStatus;
import com.example.avalon.runtime.model.GameRuntimeState;

public class ClassicFivePlayerGameRuleEngine implements GameRuleEngine {
    private static final int[] TEAM_SIZES = {0, 2, 3, 2, 3, 3};

    @Override
    public int teamSizeForRound(int roundNo) {
        if (roundNo < 1 || roundNo >= TEAM_SIZES.length) {
            return TEAM_SIZES[TEAM_SIZES.length - 1];
        }
        return TEAM_SIZES[roundNo];
    }

    @Override
    public int missionFailThresholdForRound(int roundNo) {
        return 1;
    }

    @Override
    public boolean isGameOver(GameRuntimeState state) {
        return state.status() == GameStatus.ENDED;
    }

    @Override
    public boolean shouldEnterAssassination(GameRuntimeState state) {
        return state.approvedMissionRounds().size() >= 3 && state.failedMissionRounds().size() < 3;
    }

    @Override
    public String resolveWinner(GameRuntimeState state) {
        if (state.failedMissionRounds().size() >= 3) {
            return "EVIL";
        }
        if (state.approvedMissionRounds().size() >= 3) {
            return "GOOD";
        }
        return null;
    }
}
