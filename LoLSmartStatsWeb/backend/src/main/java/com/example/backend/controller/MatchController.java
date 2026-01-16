package com.example.backend.controller;

import com.example.backend.dto.request.MatchDetailRequest;
import com.example.backend.dto.request.MatchSearchRequest;
import com.example.backend.service.data.MatchService;
import com.example.backend.util.TraceIdUtil;
import com.example.backend.vo.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/matches")
public class MatchController {

    private final MatchService matchService;

    public MatchController(MatchService matchService) {
        this.matchService = matchService;
    }

    /**
     * 3.2 比赛列表（分页）
     */
    @PostMapping("/search")
    public ApiResponse<Map<String, Object>> search(@Valid @RequestBody MatchSearchRequest req,
                                                   Authentication authentication) {
        // 需要鉴权：只要进入这里一般代表鉴权通过；authentication 不使用也没关系
        Map<String, Object> data = matchService.search(req);
        return ApiResponse.ok(data, TraceIdUtil.getOrCreate());
    }

    /**
     * 3.3 比赛详情
     */
    @PostMapping("/detail")
    public ApiResponse<Map<String, Object>> detail(@Valid @RequestBody MatchDetailRequest req,
                                                   Authentication authentication) {
        Map<String, Object> data = matchService.detail(req.getMatchId());
        return ApiResponse.ok(data, TraceIdUtil.getOrCreate());
    }
}
