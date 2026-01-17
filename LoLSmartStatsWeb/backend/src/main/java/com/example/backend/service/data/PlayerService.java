package com.example.backend.service.data;

import com.example.backend.entity.Player;
import com.example.backend.repository.PlayerRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PlayerService {

    private final PlayerRepository playerRepository;

    public PlayerService(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    public Map<String, Object> search(String q, int page, int pageSize) {
        int p = Math.max(1, page);
        int ps = Math.max(1, pageSize);
        var pageable = PageRequest.of(p - 1, ps);

        var res = playerRepository.findByNameContainingIgnoreCase(q, pageable);
        List<Map<String, Object>> items = res.getContent().stream().map(pl -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", pl.getId());
            m.put("name", pl.getName());
            return m;
        }).toList();

        Map<String, Object> out = new HashMap<>();
        out.put("items", items);
        out.put("page", p);
        out.put("pageSize", ps);
        out.put("total", res.getTotalElements());
        return out;
    }
}

