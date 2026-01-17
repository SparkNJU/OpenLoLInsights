package com.example.backend.dto.response;

public class UserMeResponse {
    private String userId;
    private String email;
    private String nickname;
    private String avatar;

    public UserMeResponse() {}

    public UserMeResponse(String userId, String email, String nickname, String avatar) {
        this.userId = userId;
        this.email = email;
        this.nickname = nickname;
        this.avatar = avatar;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
}

