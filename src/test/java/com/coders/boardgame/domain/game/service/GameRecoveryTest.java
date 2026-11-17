package com.coders.boardgame.domain.game.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.coders.boardgame.domain.game.domain.GameTiming;
import com.coders.boardgame.domain.game.dto.GameRoomDto;
import com.coders.boardgame.domain.game.dto.PlayerDto;
import com.coders.boardgame.domain.game.enums.GamePhase;
import com.coders.boardgame.domain.game.enums.RoomStatus;
import com.coders.boardgame.domain.user.dto.UserDto;
import com.coders.boardgame.exception.GameRoomException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class GameRecoveryTest {
    private GameRoomService rooms;
    private GameSseService events;
    private GameService games;
    private GameRoomDto room;

    @BeforeEach
    void setUp() {
        rooms = mock(GameRoomService.class);
        events = mock(GameSseService.class);
        games = new GameService(rooms, events, new GameTiming(180, 180, 30));
        room =
                GameRoomDto.builder()
                        .roomId("test-room")
                        .roomName("test")
                        .hostId(1L)
                        .totalPlayers(3)
                        .currentPlayers(new AtomicInteger(3))
                        .players(new ConcurrentHashMap<>())
                        .currentRound(2)
                        .currentTurn(1)
                        .currentPhase(GamePhase.DISCUSSION)
                        .roomStatus(RoomStatus.IN_GAME)
                        .totalPuzzlePieces(10)
                        .seats(new Long[] {1L, 2L, 3L})
                        .availableAvatarIds(new ArrayList<>())
                        .build();
        for (long id = 1; id <= 3; id++) {
            UserDto info = new UserDto();
            info.setName("player-" + id);
            room.getPlayers()
                    .put(
                            id,
                            PlayerDto.builder()
                                    .playerId(id)
                                    .playerInfo(info)
                                    .sequenceNumber((int) id)
                                    .isSpeaking(id == 1)
                                    .isReady(true)
                                    .lastPingTime(System.currentTimeMillis())
                                    .build());
        }
        when(rooms.getRoom("test-room")).thenReturn(room);
    }

    @Test
    void serverEndsSpeakingEvenWhenSpeakerIsDisconnected() {
        room.getPlayers().get(1L).setDisconnectedAt(100);
        room.getProgress().setSpeakingEndsAt(1000);
        games.advanceDeadlines(room, 1000);
        games.advanceDeadlines(room, 1000);
        assertThat(room.getCurrentPhase()).isEqualTo(GamePhase.VOTING);
        assertThat(room.getProgress().getVotingEndsAt()).isEqualTo(31_000);
        verify(rooms, times(1)).publishState(room);
    }

    @Test
    void expiredVoteAbstainsWithoutBecomingDisagreement() {
        room.setCurrentPhase(GamePhase.VOTING);
        room.getProgress().setVotingEndsAt(1000);
        room.getProgress().getVotes().put(2L, "agree");
        games.advanceDeadlines(room, 1000);
        assertThat(room.getCurrentPuzzlePieces()).isEqualTo(1);
        assertThat(room.getCurrentTurn()).isEqualTo(2);
        verify(events)
                .sendRoomEvent(
                        eq("test-room"),
                        eq("vote-result"),
                        argThat(
                                value -> {
                                    var result = (java.util.Map<?, ?>) value;
                                    return result.get("abstainCount").equals(1)
                                            && result.get("disagreeCount").equals(0);
                                }));
    }

    @Test
    void allAbstentionsStartOneRevoteThenAdvanceWithoutAward() {
        room.setCurrentPhase(GamePhase.VOTING);
        room.getProgress().setVotingEndsAt(1000);
        games.advanceDeadlines(room, 1000);
        assertThat(room.isHasReVoted()).isTrue();
        games.advanceDeadlines(room, room.getProgress().getVotingEndsAt());
        assertThat(room.getCurrentTurn()).isEqualTo(2);
        assertThat(room.getCurrentPuzzlePieces()).isZero();
        assertThat(room.isHasReVoted()).isFalse();
    }

    @Test
    void simultaneousDeadlineChecksAwardOnlyOnce() throws Exception {
        room.setCurrentPhase(GamePhase.VOTING);
        room.getProgress().setVotingEndsAt(1000);
        room.getProgress().getVotes().put(2L, "agree");
        var pool = Executors.newFixedThreadPool(8);
        var start = new CountDownLatch(1);
        var futures = new ArrayList<java.util.concurrent.Future<?>>();
        try {
            for (int i = 0; i < 8; i++)
                futures.add(
                        pool.submit(
                                () -> {
                                    try {
                                        start.await();
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                    }
                                    games.advanceDeadlines(room, 1000);
                                }));
            start.countDown();
            for (var future : futures) future.get(5, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertThat(room.getCurrentPuzzlePieces()).isEqualTo(1);
        assertThat(room.getCurrentTurn()).isEqualTo(2);
        verify(rooms, times(1)).publishState(room);
    }

    @Test
    void outsidersAndListenersCannotControlSpeaking() {
        assertThatThrownBy(() -> games.assignCard("test-room", 4, "text", 99L))
                .isInstanceOf(GameRoomException.class);
        assertThatThrownBy(() -> games.endSpeaking("test-room", 2L))
                .isInstanceOf(GameRoomException.class);
        assertThat(room.getCurrentPhase()).isEqualTo(GamePhase.DISCUSSION);
    }

    @Test
    void duplicateVoteDoesNotChangeOriginalChoice() {
        room.setCurrentPhase(GamePhase.VOTING);
        room.getProgress().setVotingEndsAt(System.currentTimeMillis() + 30_000);
        games.castVote("test-room", 2L, "agree", 0);
        games.castVote("test-room", 2L, "disagree", 0);
        assertThat(room.getProgress().getVotes()).containsEntry(2L, "agree");
        assertThatThrownBy(() -> games.castVote("test-room", 1L, "agree", 0))
                .isInstanceOf(GameRoomException.class);
        assertThatThrownBy(() -> games.castVote("test-room", 99L, "agree", 0))
                .isInstanceOf(GameRoomException.class);
    }

    @Test
    void delayedVoteCannotEnterTheNextVotingRound() {
        room.setCurrentPhase(GamePhase.VOTING);
        room.getProgress().setVotingEndsAt(System.currentTimeMillis() + 30_000);
        room.getProgress().setVotingRound(2);
        assertThatThrownBy(() -> games.castVote("test-room", 2L, "agree", 1))
                .isInstanceOf(GameRoomException.class);
        assertThat(room.getProgress().getVotes()).isEmpty();
    }

    @Test
    void snapshotIncludesCardsDeadlineAndSubmittedVoters() {
        room.setAssignedTextCardId(2);
        room.setAssignedPictureCardId(3);
        room.getProgress().setSpeakingEndsAt(1234);
        room.getProgress().getVotes().put(2L, "agree");
        var state = games.getGameState("test-room", 1L);
        assertThat(state.getAssignedTextCardId()).isEqualTo(2);
        assertThat(state.getAssignedPictureCardId()).isEqualTo(3);
        assertThat(state.getSpeakingEndsAt()).isEqualTo(1234);
        assertThat(state.getVotedPlayerIds()).containsExactly(2L);
        room.getPlayers().get(1L).setCollectedPuzzlePieces(5);
        assertThat(state.getPlayers()).allMatch(p -> p.getCollectedPuzzlePieces() == 0);
    }

    @Test
    void extensionIsAllowedOnlyOnceAndMovesServerDeadline() {
        long deadline = System.currentTimeMillis() + 50_000;
        room.getProgress().setSpeakingEndsAt(deadline);
        games.extendTime("test-room", 120, 1L);
        assertThat(room.getProgress().getSpeakingEndsAt()).isEqualTo(deadline + 120_000);
        assertThatThrownBy(() -> games.extendTime("test-room", 120, 1L))
                .isInstanceOf(GameRoomException.class);
    }
}
