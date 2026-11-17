package com.coders.boardgame.domain.game.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.coders.boardgame.domain.game.domain.GameTiming;
import com.coders.boardgame.domain.game.dto.*;
import com.coders.boardgame.domain.game.enums.RoomStatus;
import com.coders.boardgame.domain.user.dto.UserDto;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class GameConnectionTest {
    @Test
    void graceExpiryEndsGameForRemainingPlayers() {
        var events = mock(GameSseService.class);
        var gameReference = new java.util.concurrent.atomic.AtomicReference<GameService>();
        ApplicationEventPublisher publisher =
                event -> {
                    if (event
                            instanceof
                            com.coders.boardgame.domain.game.event.GameEndedEvent ended) {
                        gameReference.get().resetGame(ended.getRoomId());
                    }
                };
        var timing = new GameTiming(180, 180, 30);
        var rooms = new GameRoomService(events, publisher, timing);
        gameReference.set(new GameService(rooms, events, timing));
        var host = new UserDto();
        host.setName("host");
        String id =
                rooms.createRoom(
                                CreateRoomRequestDto.builder()
                                        .roomName("test")
                                        .totalPlayers(3)
                                        .avatarMaxId(10)
                                        .hostInfo(host)
                                        .build(),
                                1L)
                        .getRoomId();
        var room = rooms.getRoom(id);
        var peer = new UserDto();
        peer.setName("peer");
        room.getPlayers()
                .put(
                        2L,
                        PlayerDto.builder()
                                .playerId(2L)
                                .playerInfo(peer)
                                .seatIndex(1)
                                .lastPingTime(System.currentTimeMillis() + 180_000)
                                .build());
        room.getSeats()[1] = 2L;
        room.getCurrentPlayers().set(2);
        room.setRoomStatus(RoomStatus.IN_GAME);
        room.setCurrentPuzzlePieces(4);
        rooms.markDisconnected(id, 1L);
        rooms.expireDisconnectedPlayers(room.getPlayers().get(1L).getDisconnectedAt() + 180_000);
        assertThat(room.getRoomStatus()).isEqualTo(RoomStatus.WAITING);
        assertThat(room.getCurrentPuzzlePieces()).isZero();
        assertThat(room.getHostId()).isEqualTo(2L);
        assertThat(room.getPlayers()).containsOnlyKeys(2L);
    }

    @Test
    void connectionEndpointSendsVersionedSnapshot() throws Exception {
        var publisher = mock(ApplicationEventPublisher.class);
        var events = new GameSseService(publisher);
        var rooms = new GameRoomService(events, publisher, new GameTiming(180, 180, 30));
        String id =
                rooms.createRoom(
                                CreateRoomRequestDto.builder()
                                        .roomName("test")
                                        .totalPlayers(3)
                                        .avatarMaxId(10)
                                        .hostInfo(new UserDto())
                                        .build(),
                                1L)
                        .getRoomId();
        var controller =
                new com.coders.boardgame.domain.game.controller.GameSseController(
                        rooms,
                        new com.coders.boardgame.domain.user.service.SessionService(),
                        events);
        var mvc =
                org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                                controller)
                        .build();
        var result =
                mvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .get("/api/sse/rooms/connect/{roomId}", id)
                                        .sessionAttr("userId", 1L))
                        .andReturn();
        String response = result.getResponse().getContentAsString();
        assertThat(response)
                .contains(
                        "event:game-state", "id:1", "\"revision\":1", "\"roomStatus\":\"WAITING\"");
    }

    @Test
    void disconnectKeepsMembershipUntilGraceDeadline() {
        var events = mock(GameSseService.class);
        var publisher = mock(ApplicationEventPublisher.class);
        var rooms = new GameRoomService(events, publisher, new GameTiming(180, 180, 30));
        var request =
                CreateRoomRequestDto.builder()
                        .roomName("test")
                        .totalPlayers(3)
                        .avatarMaxId(10)
                        .hostInfo(new UserDto())
                        .build();
        String id = rooms.createRoom(request, 1L).getRoomId();
        var room = rooms.getRoom(id);
        rooms.markDisconnected(id, 1L);
        long disconnectedAt = room.getPlayers().get(1L).getDisconnectedAt();
        rooms.expireDisconnectedPlayers(disconnectedAt + 179_999);
        assertThat(room.getPlayers()).containsKey(1L);
        rooms.expireDisconnectedPlayers(disconnectedAt + 180_000);
        assertThat(rooms.getAllRooms()).isEmpty();
    }

    @Test
    void reconnectInsideGraceRetainsGameAndClearsDisconnectTime() {
        var events = mock(GameSseService.class);
        var rooms =
                new GameRoomService(
                        events,
                        mock(ApplicationEventPublisher.class),
                        new GameTiming(180, 180, 30));
        String id =
                rooms.createRoom(
                                CreateRoomRequestDto.builder()
                                        .roomName("test")
                                        .totalPlayers(3)
                                        .avatarMaxId(10)
                                        .hostInfo(new UserDto())
                                        .build(),
                                1L)
                        .getRoomId();
        var room = rooms.getRoom(id);
        room.setRoomStatus(RoomStatus.IN_GAME);
        rooms.markDisconnected(id, 1L);
        var emitter = new org.springframework.web.servlet.mvc.method.annotation.SseEmitter();
        when(events.connectToRoom(id, 1L)).thenReturn(new ConnectionResult(emitter, true));
        rooms.connectToRoom(id, 1L);
        assertThat(room.getPlayers().get(1L).getDisconnectedAt()).isZero();
        assertThat(room.getRoomStatus()).isEqualTo(RoomStatus.IN_GAME);
        verify(events).sendSnapshot(eq(id), argThat(s -> s.getRevision() == 1));
    }
}
