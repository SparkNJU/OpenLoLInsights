package com.example.backend.controller;

import com.example.backend.dto.request.PlayerSearchRequest;
import com.example.backend.service.data.PlayerService;
import com.example.backend.util.TraceIdUtil;
import com.example.backend.vo.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/players")
public class PlayerController {

    private final PlayerService playerService;

    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    /**
     * 3.4 选手搜索
     */
    @PostMapping("/search")
    public ApiResponse<Map<String, Object>> search(@Valid @RequestBody PlayerSearchRequest req,
                                                   Authentication authentication) {
        Map<String, Object> data = playerService.search(req.getQ(), req.getPage(), req.getPageSize());
        return ApiResponse.ok(data, TraceIdUtil.getOrCreate());
    }
}

