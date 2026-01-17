package com.example.backend.dto.request;

import jakarta.validation.constraints.Pattern;

/**
 * 会话列表查询（分页 + 过滤）
 */
public class ChatSessionListRequest extends PageRequest {

    /** 会话状态：active / archived / deleted（为空表示不过滤） */
    @Pattern(regexp = "^(active|archived|deleted)?$", message = "status must be active/archived/deleted")
    private String status;

    /** ISO-8601 时间范围（Instant 字符串），如 2026-01-15T00:00:00Z */
    private String from;
    private String to;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }
    public String getTo() { return to; }
    public void setTo(String to) { this.to = to; }
}
