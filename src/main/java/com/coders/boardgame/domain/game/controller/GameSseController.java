package com.coders.boardgame.domain.game.controller;

import com.coders.boardgame.domain.game.dto.GameRoomDto;
import com.coders.boardgame.domain.game.service.GameRoomService;
import com.coders.boardgame.domain.game.service.GameSseService;
import com.coders.boardgame.domain.user.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequestMapping("/api/sse")
@RequiredArgsConstructor
public class GameSseController {

    private final GameRoomService gameRoomService;
    private final SessionService sessionService;
    private final GameSseService gameSseService;

    /**
     * 방과 SSE 연결
     *
     * @param roomId  방 id
     * @param request request 객체
     * @return emitter 객체
     */
    @GetMapping(value = "/rooms/connect/{roomId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connectToRoom(@PathVariable String roomId, HttpServletRequest request) {
        return gameRoomService.connectToRoom(roomId, sessionService.getUserIdFromSession(request));
    }

    /**
     * 해당 플레이어 sse 연결 해제
     * @param roomId
     * @param request
     * @return
     */
    @DeleteMapping("/{roomId}/players")
    public ResponseEntity<String> disconnectPlayer(@PathVariable String roomId, HttpServletRequest request) {
        GameRoomDto room = gameRoomService.getRoom(roomId);
        Long userId = sessionService.getUserIdFromSession(request);
        gameSseService.disconnectPlayer(room.getRoomId(), "disconnectByUser", userId, false);
        return ResponseEntity.ok("연결 해제 완료");
    }
}
