package com.coders.boardgame.domain.game.component;

import com.coders.boardgame.domain.game.service.GameRoomService;
import com.coders.boardgame.domain.game.service.GameService;

import lombok.RequiredArgsConstructor;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@RequiredArgsConstructor
public class GhostPlayerCleaner {
    private final GameRoomService rooms;
    private final GameService games;

    @Scheduled(fixedDelay = 1000)
    public void advanceDeadlines() {
        long now = System.currentTimeMillis();
        rooms.expireDisconnectedPlayers(now);
        for (var room : rooms.getAllRooms()) games.advanceDeadlines(room, now);
    }
}
