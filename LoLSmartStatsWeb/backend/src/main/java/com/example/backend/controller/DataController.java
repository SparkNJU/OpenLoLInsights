package com.example.backend.controller;

import com.example.backend.dto.request.DataOptionsRequest;
import com.example.backend.service.data.DataOptionsService;
import com.example.backend.util.TraceIdUtil;
import com.example.backend.vo.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/data")
public class DataController {

    private final DataOptionsService dataOptionsService;

    public DataController(DataOptionsService dataOptionsService) {
        this.dataOptionsService = dataOptionsService;
    }

    /**
     * 3.1 筛选项候选值
     */
    @PostMapping("/options")
    public ApiResponse<Map<String, Object>> options(@Valid @RequestBody DataOptionsRequest req) {
        Map<String, Object> data = dataOptionsService.options(req);
        return ApiResponse.ok(data, TraceIdUtil.getOrCreate());
    }
}

