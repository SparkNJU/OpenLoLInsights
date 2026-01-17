package com.example.backend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "Matches")
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "match_date", length = 255)
    private String matchDate;

    @Column(name = "tournament_name", length = 255)
    private String tournamentName;

    @Column(length = 255)
    private String stage;

    @Column(name = "team1_id")
    private Integer team1Id;

    @Column(name = "team2_id")
    private Integer team2Id;

    @Column(name = "winner_id")
    private Integer winnerId;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getMatchDate() { return matchDate; }
    public void setMatchDate(String matchDate) { this.matchDate = matchDate; }

    public String getTournamentName() { return tournamentName; }
    public void setTournamentName(String tournamentName) { this.tournamentName = tournamentName; }

    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }

    public Integer getTeam1Id() { return team1Id; }
    public void setTeam1Id(Integer team1Id) { this.team1Id = team1Id; }

    public Integer getTeam2Id() { return team2Id; }
    public void setTeam2Id(Integer team2Id) { this.team2Id = team2Id; }

    public Integer getWinnerId() { return winnerId; }
    public void setWinnerId(Integer winnerId) { this.winnerId = winnerId; }
}
