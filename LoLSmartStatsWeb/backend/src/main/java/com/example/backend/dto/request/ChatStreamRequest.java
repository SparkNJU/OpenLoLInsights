package com.example.backend.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public class ChatStreamRequest {
    @NotBlank
    private String sessionId;

    @NotBlank
    private String message;

    private String mode;

    private Map<String, Object> context;

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public Map<String, Object> getContext() { return context; }
    public void setContext(Map<String, Object> context) { this.context = context; }
}

