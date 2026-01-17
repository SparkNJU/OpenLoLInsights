package com.example.backend.service.auth;

import com.example.backend.dto.response.AuthResponse;
import com.example.backend.dto.response.TokenResponse;
import com.example.backend.dto.response.UserMeResponse;
import com.example.backend.entity.User;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserService userService;
    private final TokenService tokenService;

    public AuthService(UserService userService, TokenService tokenService) {
        this.userService = userService;
        this.tokenService = tokenService;
    }

    public AuthResponse register(String email, String password, String nickname) {
        User u = userService.register(email, password, nickname);
        return buildAuth(u);
    }

    public AuthResponse login(String email, String password) {
        User u = userService.checkLogin(email, password);
        return buildAuth(u);
    }

    public TokenResponse refresh(String refreshToken) {
        TokenService.RefreshResult r = tokenService.rotateRefreshToken(refreshToken);
        return new TokenResponse(r.accessToken(), r.refreshToken());
    }

    public void logout(String refreshToken) {
        tokenService.revoke(refreshToken);
    }

    private AuthResponse buildAuth(User u) {
        String access = tokenService.issueAccessToken(u.getId());
        String refresh = tokenService.issueAndStoreRefreshToken(u.getId());
        UserMeResponse me = new UserMeResponse(u.getId(), u.getEmail(), u.getNickname(), u.getAvatar());
        return new AuthResponse(me, new TokenResponse(access, refresh));
    }
}
