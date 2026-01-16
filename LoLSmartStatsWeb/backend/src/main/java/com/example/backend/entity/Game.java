package com.example.backend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "Games")
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "match_id")
    private Integer matchId;

    @Column(name = "game_number")
    private Integer gameNumber;

    private Integer duration;

    @Column(name = "blue_team_id")
    private Integer blueTeamId;

    @Column(name = "red_team_id")
    private Integer redTeamId;

    @Column(name = "winner_id")
    private Integer winnerId;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getMatchId() {
        return matchId;
    }

    public void setMatchId(Integer matchId) {
        this.matchId = matchId;
    }

    public Integer getGameNumber() {
        return gameNumber;
    }

    public void setGameNumber(Integer gameNumber) {
        this.gameNumber = gameNumber;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    public Integer getBlueTeamId() {
        return blueTeamId;
    }

    public void setBlueTeamId(Integer blueTeamId) {
        this.blueTeamId = blueTeamId;
    }

    public Integer getRedTeamId() {
        return redTeamId;
    }

    public void setRedTeamId(Integer redTeamId) {
        this.redTeamId = redTeamId;
    }

    public Integer getWinnerId() {
        return winnerId;
    }

    public void setWinnerId(Integer winnerId) {
        this.winnerId = winnerId;
    }
}
