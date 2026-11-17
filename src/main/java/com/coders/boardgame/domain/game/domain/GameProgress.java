package com.coders.boardgame.domain.game.domain;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Mutable only while holding the owning room's monitor. */
@Getter
@Setter
public class GameProgress {
    private long revision;
    private long votingRound;
    private long speakingEndsAt;
    private long votingEndsAt;
    private boolean timeExtended;
    private final Map<Long, String> votes = new HashMap<>();
    private Map<String, Object> lastVoteResult = Map.of();
    private List<Map<String, Object>> ranking = List.of();

    public void resetTurn() {
        speakingEndsAt = 0;
        votingEndsAt = 0;
        timeExtended = false;
        votes.clear();
    }

    public void resetGame() {
        resetTurn();
        ranking = List.of();
        lastVoteResult = Map.of();
    }
}
