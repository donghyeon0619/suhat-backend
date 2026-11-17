package com.coders.boardgame.domain.game.service;

import com.coders.boardgame.domain.game.domain.GameTiming;
import com.coders.boardgame.domain.game.dto.*;
import com.coders.boardgame.domain.game.enums.RoomStatus;
import com.coders.boardgame.domain.game.event.GameEndedEvent;
import com.coders.boardgame.domain.user.dto.UserDto;
import com.coders.boardgame.exception.GameRoomException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/** 게임 방 관련 service */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameRoomService {

    // 방 관리 : 방 ID -> 방정보
    private final Map<String, GameRoomDto> gameRooms = new ConcurrentHashMap<>();

    // 게임 SSE 서비스
    private final GameSseService gameSseService;

    private final ApplicationEventPublisher applicationEventPublisher;
    private final GameTiming timing;

    /**
     * UUID 기반 8자리 Room ID 생성
     *
     * @return roomId
     */
    public String generateRoomId() {
        int attempts = 0;
        int maxAttempts = 100;

        String roomId;
        do {
            if (attempts++ >= maxAttempts) {
                throw new IllegalStateException("Room ID 생성에 실패했습니다.");
            }
            roomId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        } while (gameRooms.containsKey(roomId));

        return roomId;
    }

    /**
     * 게임 방 생성 함수
     *
     * @param requestDto
     * @return CreateRoomResponseDto 객체 반환
     */
    public CreateRoomResponseDto createRoom(CreateRoomRequestDto requestDto, Long userId) {
        String roomId = generateRoomId();

        // [1] 우선 사용할 수 있는 아바타 목록을 만듬
        List<Integer> avatarList = new ArrayList<>();
        for (int i = 1; i <= requestDto.getAvatarMaxId(); i++) {
            avatarList.add(i);
        }

        // [2] 방 정보 생성
        GameRoomDto gameRoom =
                GameRoomDto.builder()
                        .roomId(roomId)
                        .roomName(requestDto.getRoomName())
                        .totalPlayers(requestDto.getTotalPlayers())
                        .currentPlayers(new AtomicInteger(0))
                        .hostId(userId)
                        .players(new ConcurrentHashMap<>())
                        .currentTurn(0)
                        .totalPuzzlePieces(requestDto.getTotalPlayers() == 3 ? 10 : 13)
                        .currentPuzzlePieces(0)
                        .assignedPictureCardId(0)
                        .assignedTextCardId(0)
                        .roomStatus(RoomStatus.WAITING)
                        .seats(new Long[requestDto.getTotalPlayers()])
                        .availableAvatarIds(avatarList) // 아바타 목록 세팅
                        .build();

        // 방을 먼저 생성
        gameRooms.put(roomId, gameRoom);

        // [3] 호스트를 생성 & avatarId 배정
        PlayerDto host =
                createNewPlayer(
                        userId, roomId, requestDto.getHostInfo(), requestDto.getSurveyScore());

        gameRoom.getSeats()[0] = userId;
        host.setSeatIndex(0);

        // players 에 추가
        gameRoom.getPlayers().put(host.getPlayerId(), host);
        gameRoom.getCurrentPlayers().incrementAndGet(); // 호스트 1명 추가

        return CreateRoomResponseDto.builder()
                .roomId(roomId)
                .roomName(requestDto.getRoomName())
                .totalPlayers(requestDto.getTotalPlayers())
                .host(host)
                .build();
    }

    /**
     * 방 조회 함수
     *
     * @param roomId 방 id
     * @return room 정보 반환
     */
    public GameRoomDto getRoom(String roomId) {
        GameRoomDto room = gameRooms.get(roomId);
        if (room == null) {
            throw new GameRoomException("방이 존재하지 않습니다: " + roomId, HttpStatus.NOT_FOUND);
        }
        return room;
    }

    public List<GameRoomDto> getAllRooms() {
        return new ArrayList<>(gameRooms.values());
    }

    /**
     * 생성된 방 sse 연결
     *
     * @param roomId 방 id
     * @return SseEmitter 객체
     */
    public SseEmitter connectToRoom(String roomId, Long playerId) {
        GameRoomDto room = getRoom(roomId);
        synchronized (room) {
            PlayerDto player = room.getPlayers().get(playerId);
            if (player == null) {
                throw new GameRoomException("방에 참여하고 있지 않습니다.", HttpStatus.FORBIDDEN);
            }
            long now = System.currentTimeMillis();
            long deadline =
                    player.getDisconnectedAt() > 0
                            ? player.getDisconnectedAt() + timing.getReconnectMillis()
                            : player.getLastPingTime() + timing.getReconnectMillis();
            if (now >= deadline) {
                leaveRoom(roomId, playerId, true);
                throw new GameRoomException("재접속 대기 시간이 만료되었습니다.", HttpStatus.GONE);
            }
            boolean reconnecting = player.getDisconnectedAt() > 0;
            player.setDisconnectedAt(0);
            player.setLastPingTime(now);
            if (room.getRoomStatus() == RoomStatus.WAITING) player.setReady(true);
            SseEmitter emitter = gameSseService.connectToRoom(roomId, playerId).emitter();
            gameSseService.sendToSpecificPlayer(roomId, playerId, "connected", "연결완료");
            if (room.getRoomStatus() == RoomStatus.WAITING) {
                gameSseService.sendRoomEventToOthers(
                        roomId,
                        reconnecting ? "player-reconnected" : "player-joined",
                        player,
                        playerId);
            }
            publishState(room);
            return emitter;
        }
    }

    public void markDisconnected(String roomId, Long playerId) {
        GameRoomDto room = gameRooms.get(roomId);
        if (room == null) return;
        synchronized (room) {
            PlayerDto player = room.getPlayers().get(playerId);
            if (player != null
                    && player.getDisconnectedAt() == 0
                    && !gameSseService.hasConnection(roomId, playerId)) {
                player.setDisconnectedAt(System.currentTimeMillis());
                player.setReady(false);
            }
        }
    }

    public void expireDisconnectedPlayers(long now) {
        for (GameRoomDto room : getAllRooms()) {
            synchronized (room) {
                for (PlayerDto player : new ArrayList<>(room.getPlayers().values())) {
                    long lastSeen =
                            player.getDisconnectedAt() > 0
                                    ? player.getDisconnectedAt()
                                    : player.getLastPingTime();
                    if (now - lastSeen >= timing.getReconnectMillis()) {
                        leaveRoom(room.getRoomId(), player.getPlayerId(), true);
                    }
                }
            }
        }
    }

    public void publishState(GameRoomDto room) {
        room.getProgress().setRevision(room.getProgress().getRevision() + 1);
        gameSseService.sendSnapshot(room.getRoomId(), GameStateMapper.snapshot(room));
    }

    /**
     * 방 참가
     *
     * @param roomId 방 ID
     * @param userId 유저 ID
     * @param joinRoomRequestDto 방 참여 요구 DTO
     * @return playerDto
     */
    public WaitingRoomDto joinRoom(
            String roomId, Long userId, JoinRoomRequestDto joinRoomRequestDto) {
        GameRoomDto room = getRoom(roomId);
        synchronized (room) {
            if (room.getRoomStatus().equals(RoomStatus.IN_GAME)
                    || room.getRoomStatus().equals(RoomStatus.ENDED)) {
                throw new GameRoomException(
                        "게임이 시작되거나 종료중입니다. 잠시후 다시 시도해주세요", HttpStatus.FORBIDDEN);
            }

            // 플레이어가 이미 존재하는지 확인
            if (room.getPlayers().containsKey(userId)) {
                // 이미 존재하는 플레이어의 정보 가져오기
                PlayerDto existingPlayer = room.getPlayers().get(userId);
                throw new GameRoomException(
                        "'" + existingPlayer.getPlayerInfo().getName() + "'가 이미 방에 있습니다.",
                        HttpStatus.FORBIDDEN);
            }

            // 현재 플레이어 수 확인
            if (room.getCurrentPlayers().get() >= room.getTotalPlayers()) {
                throw new GameRoomException(roomId + " 방이 가득찼습니다 ", HttpStatus.FORBIDDEN);
            }

            PlayerDto player =
                    createNewPlayer(
                            userId,
                            roomId,
                            joinRoomRequestDto.getUserInfo(),
                            joinRoomRequestDto.getSurveyScore());

            synchronized (room) {
                Long[] seats = room.getSeats();
                int foundIndex = -1;
                for (int i = 0; i < seats.length; i++) {
                    if (seats[i] == null) {
                        foundIndex = i;
                        break;
                    }
                }
                if (foundIndex == -1) {
                    throw new GameRoomException("방이 가득찼습니다(동시접근).", HttpStatus.FORBIDDEN);
                }

                // 자리 배정
                seats[foundIndex] = userId;
                player.setSeatIndex(foundIndex);

                // 플레이어 리스트 업데이트에 동기화 적용
                room.getPlayers().put(userId, player);
                room.getCurrentPlayers().incrementAndGet();
            }

            return buildWaitingRoomDto(room);
        }
    }

    /**
     * 방 나가기 만약 방장이 나가게 되면 무작위로 방장이 배정되고 전부 방에 나갔을 때는 방을 폭파시킴
     *
     * @param roomId 방 id
     * @param playerId 사용자 id
     */
    public void leaveRoom(String roomId, Long playerId, boolean isIntentional) {
        GameRoomDto room = getRoom(roomId);
        synchronized (room) {

            // 방 존재 확인

            if (room == null) {
                throw new GameRoomException("방이 존재하지 않습니다: " + roomId, HttpStatus.NOT_FOUND);
            }

            PlayerDto removedPlayer;
            int seatIndex;
            int currentPlayersAfter;

            synchronized (room) {
                // 플레이어 제거
                removedPlayer = room.getPlayers().remove(playerId);
                if (removedPlayer == null) {
                    throw new GameRoomException("플레이어가 방에 존재하지 않습니다.", HttpStatus.NOT_FOUND);
                }

                // 아바타 id 회수
                int leavingAvatarId = removedPlayer.getAvatarId();
                room.getAvailableAvatarIds().add(leavingAvatarId);

                seatIndex = removedPlayer.getSeatIndex();

                // seatIndex 자리 null 처리
                if (seatIndex >= 0 && seatIndex < room.getSeats().length) {
                    room.getSeats()[seatIndex] = null;
                }
                removedPlayer.setSeatIndex(-1);

                currentPlayersAfter = room.getCurrentPlayers().decrementAndGet(); // 현재 플레이어수 감소
            }

            if (isIntentional) {
                gameSseService.disconnectPlayer(roomId, "player-left", playerId, false);
            }

            if (currentPlayersAfter <= 0) {
                deleteRoom(roomId);
                return;
            }

            if (room.getRoomStatus() == RoomStatus.IN_GAME) {
                InterruptGameAndMoveToWaitingRoom(roomId, removedPlayer);
            } else if (room.getRoomStatus() == RoomStatus.WAITING) {
                // 방장이 나간 경우 새로운 방장 무작위로 선정
                if (room.getHostId().equals(playerId)) {
                    assignNewHost(room, true);
                }
                gameSseService.sendRoomEvent(roomId, "player-left", playerId);
            } else if (room.getRoomStatus() == RoomStatus.ENDED) {
                // 방장이 나간 경우 새로운 방장 무작위로 선정
                if (room.getHostId().equals(playerId)) {
                    assignNewHost(room, false);
                }
            }
        }
    }

    /**
     * 플레이어의 준비 상태 취소
     *
     * @param roomId
     * @param playerId
     */
    public void cancelPlayerReadyStatus(String roomId, Long playerId) {
        GameRoomDto room = gameRooms.get(roomId);
        PlayerDto player = room.getPlayers().get(playerId);
        if (player == null) {
            throw new GameRoomException("해당 플레이어가 존재하지 않습니다.", HttpStatus.NOT_FOUND);
        }

        player.setReady(false);

        gameSseService.sendRoomEventToOthers(roomId, "player-ready-canceled", player, playerId);
    }

    /**
     * 호스트 재부여 여부 확인 함수
     *
     * @param roomId
     */
    public void reassignHostIfNeed(String roomId) {
        GameRoomDto room = gameRooms.get(roomId);
        if (room == null) return;

        // 호스트 id가 없는지 확인
        if (!room.getPlayers().containsKey(room.getHostId()) && !room.getPlayers().isEmpty()) {
            assignNewHost(room, false);
        }
    }

    /**
     * * 게임 종료 및 모든 플레이어를 대기방으로 이동
     *
     * @param roomId
     */
    public void InterruptGameAndMoveToWaitingRoom(String roomId, PlayerDto player) {
        GameRoomDto room = gameRooms.get(roomId);

        log.info("IN_GAME 상태에서 플레이어 {}가 나갔으므로 방 {} 게임 종료 진행", player.getPlayerId(), roomId);
        String reason = player.getPlayerInfo().getName() + "가 나갔습니다.";

        // 방상태 ended로 변환
        room.setRoomStatus(RoomStatus.WAITING);

        Map<String, String> eventData = Map.of("reason", reason, "data", "게임이 초기화되어 대기방으로 이동합니다.");

        // 모든 플레이어에게 게임 종료 이벤트 전송
        gameSseService.sendRoomEvent(roomId, "game-reset", eventData);

        // gameEvent 발행
        applicationEventPublisher.publishEvent(new GameEndedEvent(this, roomId));
    }

    /**
     * ping/pong기법에 사용자의 ping타임 필드 업데이트 함수
     *
     * @param roomId
     * @param playerId
     */
    public void updatePingTime(String roomId, Long playerId) {
        GameRoomDto room = getRoom(roomId);
        synchronized (room) {
            PlayerDto player = room.getPlayers().get(playerId);

            if (player == null) {
                throw new GameRoomException("플레이어가 방에 없음", HttpStatus.NOT_FOUND);
            }

            player.setLastPingTime(System.currentTimeMillis());
            gameSseService.sendToSpecificPlayer(roomId, playerId, "heartbeat", "pong");
        }
    }

    /**
     * seat index 순으로 정렬해서 WaitingRoomDto 만듬
     *
     * @param room
     * @return 정렬된 WaitingRoomDto 객체
     */
    public WaitingRoomDto buildWaitingRoomDto(GameRoomDto room) {

        List<PlayerDto> seatOrdered = new ArrayList<>();
        Long[] seats = room.getSeats();

        // seat[0] -> seat[1] -> ... 순회하여 player를 seatOrdered에 넣음
        for (Long seat : seats) {
            if (seat != null) {
                PlayerDto p = room.getPlayers().get(seat);
                if (p != null) {
                    seatOrdered.add(p);
                }
            }
        }

        return WaitingRoomDto.builder()
                .roomId(room.getRoomId())
                .roomName(room.getRoomName())
                .currentPlayers(room.getCurrentPlayers().get())
                .totalPlayers(room.getTotalPlayers())
                .hostId(room.getHostId())
                .players(seatOrdered)
                .roomStatus(room.getRoomStatus())
                .build();
    }

    /**
     * 새로운 플레이어 생성 함수 avartarId 중복 부여 방지
     *
     * @param userId
     * @param roomId
     * @param userInfo
     * @param surveyScore
     * @return
     */
    private PlayerDto createNewPlayer(
            Long userId, String roomId, UserDto userInfo, int surveyScore) {

        GameRoomDto room = getRoom(roomId);

        int randomIndex;
        int chosenAvatar;

        synchronized (room) {
            // availableAvatarIds가 비어있다면 더이상 배정 불가
            if (room.getAvailableAvatarIds().isEmpty()) {
                throw new GameRoomException("인원이 초과되었습니다.", HttpStatus.FORBIDDEN);
            }

            // [1] 랜덤하게 하나 뽑아서 할당
            List<Integer> list = room.getAvailableAvatarIds();
            randomIndex = ThreadLocalRandom.current().nextInt(list.size());
            chosenAvatar = list.get(randomIndex);

            // 중복 방지를 위해 제거
            list.remove(randomIndex);
        }

        return PlayerDto.builder()
                .playerId(userId)
                .avatarId(chosenAvatar)
                .playerInfo(userInfo)
                .surveyScore(surveyScore)
                .usageTime(0)
                .sequenceNumber(0)
                .collectedPuzzlePieces(0)
                .lastPingTime(System.currentTimeMillis())
                .isSpeaking(false)
                .isReady(false)
                .seatIndex(-1)
                .build();
    }

    /**
     * 호스트 재선정
     *
     * @param room
     */
    private void assignNewHost(GameRoomDto room, boolean isEventRequired) {
        Long newHostId = room.getPlayers().keySet().iterator().next(); // 남은 플레이어 중 하나를 방장으로 설정
        room.setHostId(newHostId);

        if (isEventRequired) {
            // 새방장의 이름 가져오기
            String newHostName = room.getPlayers().get(newHostId).getPlayerInfo().getName();

            room.getPlayers()
                    .forEach(
                            (playerId, player) -> {
                                Map<String, Object> eventData = new HashMap<>();
                                String eventName;

                                if (playerId.equals(newHostId)) {
                                    eventData.put("message", "당신이 새로운 방장입니다.");
                                    eventData.put("isHost", true);
                                    eventName = "host-assigned";
                                } else {
                                    // 다른 사용자들에게 새 방장 정보 알림
                                    eventData.put("message", "새로운 방장은 " + newHostName + "입니다.");
                                    eventData.put("hostId", newHostId);
                                    eventName = "host-changed";
                                }

                                // 특정 사용자에게 이벤트 전송
                                gameSseService.sendToSpecificPlayer(
                                        room.getRoomId(), playerId, eventName, eventData);
                            });
        }

        log.info("새로운 방장이 지정되었습니다: roomId={}, newHostId={}", room.getRoomId(), newHostId);
    }

    /**
     * 방 삭제 함수
     *
     * @param roomId
     */
    private void deleteRoom(String roomId) {
        gameRooms.remove(roomId);
        gameSseService.removeEmitters(roomId);
        log.info("방이 삭제되었습니다: roomId={}", roomId);
    }
}
