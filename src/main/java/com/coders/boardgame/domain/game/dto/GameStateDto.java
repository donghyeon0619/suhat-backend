package com.coders.boardgame.domain.game.dto;

import com.coders.boardgame.domain.game.enums.GamePhase;
import com.coders.boardgame.domain.game.enums.RoomStatus;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class GameStateDto {
    private Map<String, Object> lastVoteResult;
    private long revision;
    private long votingRound;
    private long serverTime;
    private RoomStatus roomStatus;
    private long speakingEndsAt;
    private long votingEndsAt;
    private boolean timeExtended;
    private boolean reVoting;
    private List<Long> votedPlayerIds;
    private List<Map<String, Object>> ranking;
    private String roomId;
    private String roomName;
    private int totalPlayers;
    private int totalPuzzlePieces;
    private int currentPuzzlePieces;
    private int currentTurn;
    private int currentRound;
    private Long hostId;
    private List<PlayerDto> players;
    private GamePhase currentPhase;
    private int assignedPictureCardId;
    private int assignedTextCardId;
}
