package com.example.backend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "PlayerGameStats")
public class PlayerGameStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "game_id")
    private Integer gameId;

    @Column(name = "player_id")
    private Integer playerId;

    @Column(name = "team_id")
    private Integer teamId;

    @Column(length = 255)
    private String position;

    @Column(name = "champion_name", length = 255)
    private String championName;

    @Column(name = "champion_name_en", length = 255)
    private String championNameEn;

    @Column(name = "player_level")
    private Integer playerLevel;

    private Integer kills;
    private Integer deaths;
    private Integer assists;

    private Double kda;

    @Column(name = "kill_participation")
    private Double killParticipation;

    @Column(name = "total_damage_dealt")
    private Integer totalDamageDealt;

    @Column(name = "damage_dealt_to_champions")
    private Integer damageDealtToChampions;

    @Column(name = "damage_dealt_percentage")
    private Double damageDealtPercentage;

    @Column(name = "total_damage_taken")
    private Integer totalDamageTaken;

    @Column(name = "damage_taken_percentage")
    private Double damageTakenPercentage;

    @Column(name = "gold_earned")
    private Integer goldEarned;

    @Column(name = "minions_killed")
    private Integer minionsKilled;

    @Column(name = "is_mvp", length = 255)
    private String isMvp;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getGameId() { return gameId; }
    public void setGameId(Integer gameId) { this.gameId = gameId; }

    public Integer getPlayerId() { return playerId; }
    public void setPlayerId(Integer playerId) { this.playerId = playerId; }

    public Integer getTeamId() { return teamId; }
    public void setTeamId(Integer teamId) { this.teamId = teamId; }

    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }

    public String getChampionName() { return championName; }
    public void setChampionName(String championName) { this.championName = championName; }

    public String getChampionNameEn() { return championNameEn; }
    public void setChampionNameEn(String championNameEn) { this.championNameEn = championNameEn; }

    public Integer getPlayerLevel() { return playerLevel; }
    public void setPlayerLevel(Integer playerLevel) { this.playerLevel = playerLevel; }

    public Integer getKills() { return kills; }
    public void setKills(Integer kills) { this.kills = kills; }

    public Integer getDeaths() { return deaths; }
    public void setDeaths(Integer deaths) { this.deaths = deaths; }

    public Integer getAssists() { return assists; }
    public void setAssists(Integer assists) { this.assists = assists; }

    public Double getKda() { return kda; }
    public void setKda(Double kda) { this.kda = kda; }

    public Double getKillParticipation() { return killParticipation; }
    public void setKillParticipation(Double killParticipation) { this.killParticipation = killParticipation; }

    public Integer getTotalDamageDealt() { return totalDamageDealt; }
    public void setTotalDamageDealt(Integer totalDamageDealt) { this.totalDamageDealt = totalDamageDealt; }

    public Integer getDamageDealtToChampions() { return damageDealtToChampions; }
    public void setDamageDealtToChampions(Integer damageDealtToChampions) { this.damageDealtToChampions = damageDealtToChampions; }

    public Double getDamageDealtPercentage() { return damageDealtPercentage; }
    public void setDamageDealtPercentage(Double damageDealtPercentage) { this.damageDealtPercentage = damageDealtPercentage; }

    public Integer getTotalDamageTaken() { return totalDamageTaken; }
    public void setTotalDamageTaken(Integer totalDamageTaken) { this.totalDamageTaken = totalDamageTaken; }

    public Double getDamageTakenPercentage() { return damageTakenPercentage; }
    public void setDamageTakenPercentage(Double damageTakenPercentage) { this.damageTakenPercentage = damageTakenPercentage; }

    public Integer getGoldEarned() { return goldEarned; }
    public void setGoldEarned(Integer goldEarned) { this.goldEarned = goldEarned; }

    public Integer getMinionsKilled() { return minionsKilled; }
    public void setMinionsKilled(Integer minionsKilled) { this.minionsKilled = minionsKilled; }

    public String getIsMvp() { return isMvp; }
    public void setIsMvp(String isMvp) { this.isMvp = isMvp; }
}
