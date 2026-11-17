package com.coders.boardgame.domain.game.service;

import com.coders.boardgame.domain.game.dto.GameRoomDto;
import com.coders.boardgame.domain.game.dto.GameStateDto;

import java.util.List;

final class GameStateMapper {
    private GameStateMapper() {}

    // Caller holds the room monitor so the snapshot and revision describe one state.
    static GameStateDto snapshot(GameRoomDto room) {
        var progress = room.getProgress();
        return GameStateDto.builder()
                .roomId(room.getRoomId())
                .roomName(room.getRoomName())
                .hostId(room.getHostId())
                .roomStatus(room.getRoomStatus())
                .totalPlayers(room.getTotalPlayers())
                .totalPuzzlePieces(room.getTotalPuzzlePieces())
                .currentPuzzlePieces(room.getCurrentPuzzlePieces())
                .currentTurn(room.getCurrentTurn())
                .currentRound(room.getCurrentRound())
                .currentPhase(room.getCurrentPhase())
                .assignedPictureCardId(room.getAssignedPictureCardId())
                .assignedTextCardId(room.getAssignedTextCardId())
                .players(
                        room.getPlayers().values().stream()
                                .map(p -> p.toBuilder().build())
                                .toList())
                .revision(progress.getRevision())
                .serverTime(System.currentTimeMillis())
                .speakingEndsAt(progress.getSpeakingEndsAt())
                .votingEndsAt(progress.getVotingEndsAt())
                .votingRound(progress.getVotingRound())
                .timeExtended(progress.isTimeExtended())
                .reVoting(room.isHasReVoted())
                .votedPlayerIds(List.copyOf(progress.getVotes().keySet()))
                .ranking(progress.getRanking())
                .lastVoteResult(progress.getLastVoteResult())
                .build();
    }
}
