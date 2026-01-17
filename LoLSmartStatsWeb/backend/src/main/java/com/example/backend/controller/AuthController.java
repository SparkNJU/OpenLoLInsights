package com.example.backend.controller;

import com.example.backend.dto.request.LoginRequest;
import com.example.backend.dto.request.RefreshTokenRequest;
import com.example.backend.dto.request.RegisterRequest;
import com.example.backend.dto.response.AuthResponse;
import com.example.backend.dto.response.TokenResponse;
import com.example.backend.service.auth.AuthService;
import com.example.backend.util.TraceIdUtil;
import com.example.backend.vo.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        AuthResponse data = authService.register(req.getEmail(), req.getPassword(), req.getNickname());
        return ApiResponse.ok(data, TraceIdUtil.getOrCreate());
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        AuthResponse data = authService.login(req.getEmail(), req.getPassword());
        return ApiResponse.ok(data, TraceIdUtil.getOrCreate());
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest req) {
        TokenResponse data = authService.refresh(req.getRefreshToken());
        return ApiResponse.ok(data, TraceIdUtil.getOrCreate());
    }

    @PostMapping("/logout")
    public ApiResponse<Map<String, Boolean>> logout(@Valid @RequestBody RefreshTokenRequest req) {
        authService.logout(req.getRefreshToken());
        HashMap<String, Boolean> data = new HashMap<>();
        data.put("ok", true);
        return ApiResponse.ok(data, TraceIdUtil.getOrCreate());
    }
}
