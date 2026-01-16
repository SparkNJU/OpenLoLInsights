package com.example.backend.service.data;

import com.example.backend.dto.request.MatchSearchRequest;
import com.example.backend.entity.Game;
import com.example.backend.entity.Match;
import com.example.backend.entity.Player;
import com.example.backend.entity.PlayerGameStat;
import com.example.backend.entity.Team;
import com.example.backend.exception.BizException;
import com.example.backend.repository.GameRepository;
import com.example.backend.repository.MatchRepository;
import com.example.backend.repository.PlayerGameStatRepository;
import com.example.backend.repository.PlayerRepository;
import com.example.backend.repository.TeamRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class MatchService {

    private final MatchRepository matchRepository;
    private final GameRepository gameRepository;
    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final PlayerGameStatRepository playerGameStatRepository;

    @PersistenceContext
    private EntityManager em;

    public MatchService(MatchRepository matchRepository,
                        GameRepository gameRepository,
                        TeamRepository teamRepository,
                        PlayerRepository playerRepository,
                        PlayerGameStatRepository playerGameStatRepository) {
        this.matchRepository = matchRepository;
        this.gameRepository = gameRepository;
        this.teamRepository = teamRepository;
        this.playerRepository = playerRepository;
        this.playerGameStatRepository = playerGameStatRepository;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> search(MatchSearchRequest req) {
        int page = req == null ? 1 : req.getPage();
        int pageSize = req == null ? 20 : req.getPageSize();
        int offset = Math.max(0, (page - 1) * pageSize);

        MatchSearchRequest.Filter f = req == null ? null : req.getFilter();

        String sortField = req != null && req.getSort() != null ? req.getSort().getField() : null;
        String sortOrder = req != null && req.getSort() != null ? req.getSort().getOrder() : null;

        String orderBy;
        if ("matchDate".equalsIgnoreCase(sortField)) {
            // match_date 是 varchar，排序按字符串
            orderBy = "m.match_date";
        } else {
            orderBy = "m.id";
        }
        String dir = "asc".equalsIgnoreCase(sortOrder) ? "ASC" : "DESC";

        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        Map<String, Object> params = new HashMap<>();

        if (f != null) {
            if (f.getTournamentName() != null && !f.getTournamentName().isBlank()) {
                where.append(" AND m.tournament_name = :tournamentName ");
                params.put("tournamentName", f.getTournamentName());
            }
            if (f.getStage() != null && !f.getStage().isBlank()) {
                where.append(" AND m.stage = :stage ");
                params.put("stage", f.getStage());
            }
            if (f.getTeamIds() != null && !f.getTeamIds().isEmpty()) {
                where.append(" AND (m.team1_id IN (:teamIds) OR m.team2_id IN (:teamIds)) ");
                params.put("teamIds", f.getTeamIds());
            }
            if (f.getDateRange() != null) {
                String from = f.getDateRange().getFrom();
                String to = f.getDateRange().getTo();
                // match_date 是 varchar，这里进行字符串范围过滤（要求前端按 YYYY-MM-DD 或 ISO-8601 统一格式传）
                if (from != null && !from.isBlank()) {
                    where.append(" AND m.match_date >= :from ");
                    params.put("from", from);
                }
                if (to != null && !to.isBlank()) {
                    where.append(" AND m.match_date <= :to ");
                    params.put("to", to);
                }
            }
        }

        String sqlList = "SELECT m.id, m.match_date, m.tournament_name, m.stage, m.team1_id, m.team2_id, m.winner_id " +
                "FROM Matches m " + where +
                " ORDER BY " + orderBy + " " + dir +
                " LIMIT :limit OFFSET :offset";

        String sqlCount = "SELECT COUNT(1) FROM Matches m " + where;

        var qList = em.createNativeQuery(sqlList);
        var qCount = em.createNativeQuery(sqlCount);
        for (Map.Entry<String, Object> e : params.entrySet()) {
            qList.setParameter(e.getKey(), e.getValue());
            qCount.setParameter(e.getKey(), e.getValue());
        }
        qList.setParameter("limit", pageSize);
        qList.setParameter("offset", offset);

        Number totalN = (Number) qCount.getSingleResult();
        long total = totalN == null ? 0 : totalN.longValue();

        List<Object[]> rows = qList.getResultList();

        // 预加载队伍映射，避免 N+1
        Set<Integer> teamIds = new HashSet<>();
        for (Object[] r : rows) {
            Integer t1 = r[4] == null ? null : ((Number) r[4]).intValue();
            Integer t2 = r[5] == null ? null : ((Number) r[5]).intValue();
            if (t1 != null) teamIds.add(t1);
            if (t2 != null) teamIds.add(t2);
        }
        Map<Integer, Team> teamMap = new HashMap<>();
        if (!teamIds.isEmpty()) {
            for (Team t : teamRepository.findAllById(teamIds)) {
                teamMap.put(t.getId(), t);
            }
        }

        List<Map<String, Object>> items = new ArrayList<>();
        for (Object[] r : rows) {
            Integer matchId = r[0] == null ? null : ((Number) r[0]).intValue();
            String matchDate = r[1] == null ? null : String.valueOf(r[1]);
            String tournamentName = r[2] == null ? null : String.valueOf(r[2]);
            String stage = r[3] == null ? null : String.valueOf(r[3]);
            Integer team1Id = r[4] == null ? null : ((Number) r[4]).intValue();
            Integer team2Id = r[5] == null ? null : ((Number) r[5]).intValue();
            Integer winnerId = r[6] == null ? null : ((Number) r[6]).intValue();

            Map<String, Object> m = new HashMap<>();
            m.put("matchId", matchId);
            m.put("matchDate", matchDate);
            m.put("tournamentName", tournamentName);
            m.put("stage", stage);
            m.put("winnerTeamId", winnerId);

            m.put("team1", toTeamBrief(teamMap.get(team1Id), team1Id));
            m.put("team2", toTeamBrief(teamMap.get(team2Id), team2Id));

            long gamesCount = matchId == null ? 0 : gameRepository.countByMatchId(matchId);
            m.put("gamesCount", gamesCount);

            items.add(m);
        }

        Map<String, Object> out = new HashMap<>();
        out.put("items", items);
        out.put("page", page);
        out.put("pageSize", pageSize);
        out.put("total", total);
        return out;
    }

    public Map<String, Object> detail(Integer matchId) {
        if (matchId == null) throw new BizException("INVALID_ARGUMENT", "matchId 不能为空");

        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new BizException("NOT_FOUND", "比赛不存在"));

        // teams
        Map<String, Object> teams = new HashMap<>();
        teams.put("team1", toTeamFull(teamRepository.findById(match.getTeam1Id()).orElse(null), match.getTeam1Id()));
        teams.put("team2", toTeamFull(teamRepository.findById(match.getTeam2Id()).orElse(null), match.getTeam2Id()));

        // games
        List<Game> games = gameRepository.findByMatchIdOrderByGameNumberAsc(matchId);
        List<Map<String, Object>> gameItems = new ArrayList<>();

        for (Game g : games) {
            List<PlayerGameStat> stats = playerGameStatRepository.findByGameId(g.getId());

            // 批量加载 playerName
            Set<Integer> playerIds = new HashSet<>();
            for (PlayerGameStat s : stats) {
                if (s.getPlayerId() != null) playerIds.add(s.getPlayerId());
            }
            Map<Integer, String> playerNameMap = new HashMap<>();
            if (!playerIds.isEmpty()) {
                for (Player p : playerRepository.findAllById(playerIds)) {
                    playerNameMap.put(p.getId(), p.getName());
                }
            }

            List<Map<String, Object>> participants = new ArrayList<>();
            for (PlayerGameStat s : stats) {
                Map<String, Object> p = new HashMap<>();
                p.put("playerId", s.getPlayerId());
                p.put("playerName", playerNameMap.get(s.getPlayerId()));
                p.put("teamId", s.getTeamId());
                p.put("position", s.getPosition());
                p.put("championName", s.getChampionName());
                p.put("championNameEn", s.getChampionNameEn());

                Map<String, Object> st = new HashMap<>();
                st.put("kills", s.getKills());
                st.put("deaths", s.getDeaths());
                st.put("assists", s.getAssists());
                st.put("kda", s.getKda());
                st.put("killParticipation", s.getKillParticipation());
                st.put("totalDamageDealt", s.getTotalDamageDealt());
                st.put("damageDealtToChampions", s.getDamageDealtToChampions());
                st.put("damageDealtPercentage", s.getDamageDealtPercentage());
                st.put("totalDamageTaken", s.getTotalDamageTaken());
                st.put("damageTakenPercentage", s.getDamageTakenPercentage());
                st.put("goldEarned", s.getGoldEarned());
                st.put("minionsKilled", s.getMinionsKilled());
                st.put("isMvp", s.getIsMvp());
                p.put("stats", st);

                participants.add(p);
            }

            Map<String, Object> gg = new HashMap<>();
            gg.put("gameId", g.getId());
            gg.put("gameNumber", g.getGameNumber());
            gg.put("duration", g.getDuration());
            gg.put("blueTeamId", g.getBlueTeamId());
            gg.put("redTeamId", g.getRedTeamId());
            gg.put("winnerTeamId", g.getWinnerId());
            gg.put("participants", participants);
            gameItems.add(gg);
        }

        Map<String, Object> matchObj = new HashMap<>();
        matchObj.put("id", match.getId());
        matchObj.put("matchDate", match.getMatchDate());
        matchObj.put("tournamentName", match.getTournamentName());
        matchObj.put("stage", match.getStage());
        matchObj.put("team1Id", match.getTeam1Id());
        matchObj.put("team2Id", match.getTeam2Id());
        matchObj.put("winnerTeamId", match.getWinnerId());

        Map<String, Object> out = new HashMap<>();
        out.put("match", matchObj);
        out.put("teams", teams);
        out.put("games", gameItems);
        return out;
    }

    private static Map<String, Object> toTeamFull(Team t, Integer fallbackId) {
        Map<String, Object> out = new HashMap<>();
        out.put("id", t == null ? fallbackId : t.getId());
        if (t != null) {
            out.put("name", t.getName());
            out.put("shortName", t.getShortName());
            out.put("region", t.getRegion());
        }
        return out;
    }

    private static Map<String, Object> toTeamBrief(Team t, Integer fallbackId) {
        Map<String, Object> out = new HashMap<>();
        out.put("id", t == null ? fallbackId : t.getId());
        if (t != null) {
            out.put("name", t.getName());
            out.put("shortName", t.getShortName());
        }
        return out;
    }
}
