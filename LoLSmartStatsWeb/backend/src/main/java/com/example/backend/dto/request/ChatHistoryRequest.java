package com.example.backend.dto.request;

import jakarta.validation.constraints.NotBlank;

public class ChatHistoryRequest extends PageRequest {
    @NotBlank
    private String sessionId;

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
}
