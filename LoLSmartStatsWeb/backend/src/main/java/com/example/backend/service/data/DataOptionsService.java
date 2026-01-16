package com.example.backend.service.data;

import com.example.backend.dto.request.DataOptionsRequest;
import com.example.backend.repository.PlayerGameStatRepository;
import com.example.backend.repository.PlayerRepository;
import com.example.backend.repository.TeamRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DataOptionsService {

    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final PlayerGameStatRepository playerGameStatRepository;

    @PersistenceContext
    private EntityManager em;

    public DataOptionsService(TeamRepository teamRepository,
                              PlayerRepository playerRepository,
                              PlayerGameStatRepository playerGameStatRepository) {
        this.teamRepository = teamRepository;
        this.playerRepository = playerRepository;
        this.playerGameStatRepository = playerGameStatRepository;
    }

    public Map<String, Object> options(DataOptionsRequest req) {
        Set<String> need = normalizeNeed(req == null ? null : req.getNeed());
        Map<String, Object> out = new HashMap<>();

        if (need.contains("teams")) {
            out.put("teams", teamRepository.findAll().stream().map(t -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", t.getId());
                m.put("name", t.getName());
                m.put("shortName", t.getShortName());
                m.put("region", t.getRegion());
                return m;
            }).toList());
        }

        if (need.contains("players")) {
            out.put("players", playerRepository.findAll().stream().map(p -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", p.getId());
                m.put("name", p.getName());
                return m;
            }).toList());
        }

        // tournaments/stages 直接从 Matches 表 DISTINCT（用 JPQL/Native 均可，这里走 native）
        if (need.contains("tournaments")) {
            List<String> tournaments = em.createNativeQuery("SELECT DISTINCT tournament_name FROM Matches WHERE tournament_name IS NOT NULL AND tournament_name <> '' ORDER BY tournament_name")
                    .getResultList();
            out.put("tournaments", tournaments);
        }
        if (need.contains("stages")) {
            List<String> stages = em.createNativeQuery("SELECT DISTINCT stage FROM Matches WHERE stage IS NOT NULL AND stage <> '' ORDER BY stage")
                    .getResultList();
            out.put("stages", stages);
        }

        // positions/champions 从 PlayerGameStats DISTINCT
        if (need.contains("positions")) {
            List<String> positions = em.createNativeQuery("SELECT DISTINCT position FROM PlayerGameStats WHERE position IS NOT NULL AND position <> '' ORDER BY position")
                    .getResultList();
            out.put("positions", positions);
        }

        if (need.contains("champions")) {
            @SuppressWarnings("unchecked")
            List<Object[]> rows = em.createNativeQuery(
                            "SELECT DISTINCT champion_name, champion_name_en FROM PlayerGameStats " +
                                    "WHERE champion_name IS NOT NULL AND champion_name <> ''")
                    .getResultList();
            List<Map<String, Object>> champions = new ArrayList<>();
            for (Object[] r : rows) {
                Map<String, Object> c = new HashMap<>();
                c.put("name", r[0] == null ? null : String.valueOf(r[0]));
                c.put("nameEn", r[1] == null ? null : String.valueOf(r[1]));
                champions.add(c);
            }
            // 稳定排序：按 name
            champions.sort(Comparator.comparing(o -> String.valueOf(o.getOrDefault("name", ""))));
            out.put("champions", champions);
        }

        return out;
    }

    private static Set<String> normalizeNeed(List<String> need) {
        Set<String> out = new HashSet<>();
        if (need == null) return out;
        for (String n : need) {
            if (n == null) continue;
            String v = n.trim();
            if (!v.isEmpty()) out.add(v);
        }
        return out;
    }
}

