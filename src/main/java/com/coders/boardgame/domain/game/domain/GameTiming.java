package com.coders.boardgame.domain.game.domain;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
public class GameTiming {
    private final long reconnectMillis;
    private final long speakingMillis;
    private final long votingMillis;

    public GameTiming(
            @Value("${game.reconnect-seconds:180}") long reconnectSeconds,
            @Value("${game.speaking-seconds:180}") long speakingSeconds,
            @Value("${game.voting-seconds:30}") long votingSeconds) {
        if (reconnectSeconds <= 0 || speakingSeconds <= 0 || votingSeconds <= 0) {
            throw new IllegalArgumentException("Game timeouts must be positive");
        }
        this.reconnectMillis = Math.multiplyExact(reconnectSeconds, 1000);
        this.speakingMillis = Math.multiplyExact(speakingSeconds, 1000);
        this.votingMillis = Math.multiplyExact(votingSeconds, 1000);
    }
}
