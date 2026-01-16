package com.example.backend.dto.request;

import jakarta.validation.constraints.NotNull;

public class MatchDetailRequest {

    @NotNull
    private Integer matchId;

    public Integer getMatchId() { return matchId; }
    public void setMatchId(Integer matchId) { this.matchId = matchId; }
}

