package com.example.backend.controller;

import com.example.backend.dto.response.UserMeResponse;
import com.example.backend.entity.User;
import com.example.backend.service.auth.UserService;
import com.example.backend.util.TraceIdUtil;
import com.example.backend.vo.ApiResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ApiResponse<UserMeResponse> me(Authentication authentication) {
        // SecurityConfig 中 principal = userId
        String userId = authentication == null ? null : (String) authentication.getPrincipal();
        User u = userService.getById(userId);
        UserMeResponse data = new UserMeResponse(u.getId(), u.getEmail(), u.getNickname(), u.getAvatar());
        return ApiResponse.ok(data, TraceIdUtil.getOrCreate());
    }
}
