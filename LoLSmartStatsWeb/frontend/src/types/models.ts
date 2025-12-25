// src/types/index.ts

// 基础实体：战队
export interface Team {
  id: number;
  name: string;
  shortName: string;
  // region?: string; // 可选字段
}

// 基础实体：选手
export interface Player {
  id: number;
  name: string;
}

// 核心数据：选手单局表现 (对应 PlayerGameStats 表)
export interface PlayerGameStat {
  id: number;
  gameId: number;
  playerId: number;
  playerName: string;
  teamId: number;
  position: string; // 'TOP', 'JUNGLE', etc.
  championName: string;
  championNameEn: string;
  playerLevel: number;
  kills: number;
  deaths: number;
  assists: number;
  kda: number;
  killParticipation: number;
  totalDamageDealt: number;
  damageDealtPercentage: number;
  totalDamageTaken: number;
  damageTakenPercentage: number;
  goldEarned: number;
  minionsKilled: number;
  isMvp: boolean;
}

// 核心数据：单局比赛 (对应 Games 表)
export interface Game {
  id: number;
  matchId: number;
  gameNumber: number;
  duration: number;
  blueTeamId: number;
  redTeamId: number;
  winnerId: number;
  stats: PlayerGameStat[]; 
}

// 聚合数据：比赛详情 (用于详情页)
export interface MatchDetail {
  id: number;
  date: string;
  tournamentName: string;
  stage: string;
  team1: Team;
  team2: Team;
  winnerId: number;
  games: Game[];
}

// 聚合数据：比赛列表项 (用于列表页，结构可能略有不同)
export interface MatchListItem {
  id: number;
  date: string;
  tournamentName: string;
  stage: string;
  team1Id: number;
  team2Id: number;
  winnerId: number;
  team1Name: string; 
  team2Name: string;
  scoreTeam1: number; 
  scoreTeam2: number; 
}