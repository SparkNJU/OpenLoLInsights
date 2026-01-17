package com.example.backend.dto.response;

public class AuthResponse {
    private UserMeResponse user;
    private TokenResponse tokens;

    public AuthResponse() {}

    public AuthResponse(UserMeResponse user, TokenResponse tokens) {
        this.user = user;
        this.tokens = tokens;
    }

    public UserMeResponse getUser() { return user; }
    public void setUser(UserMeResponse user) { this.user = user; }
    public TokenResponse getTokens() { return tokens; }
    public void setTokens(TokenResponse tokens) { this.tokens = tokens; }
}
