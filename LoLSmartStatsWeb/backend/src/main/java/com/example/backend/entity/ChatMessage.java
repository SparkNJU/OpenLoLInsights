package com.example.backend.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "chat_messages", indexes = {
        @Index(name = "idx_chat_messages_session_created", columnList = "sessionId, createdAt"),
        @Index(name = "idx_chat_messages_turn", columnList = "turnId")
})
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String sessionId;

    @Column(nullable = false, length = 64)
    private String turnId;

    @Column(nullable = false, length = 64)
    private String traceId;

    @Column(nullable = false, length = 16)
    private String mode; // simple / report

    @Column(nullable = false, length = 16)
    private String role; // user / assistant

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    // report 文件元信息（仅 role=assistant 且 mode=report 时会有）
    @Column(length = 256)
    private String reportFileId;

    @Column(length = 256)
    private String reportFileName;

    @Column(length = 32)
    private String reportFileType; // markdown

    private Long reportSize;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getTurnId() { return turnId; }
    public void setTurnId(String turnId) { this.turnId = turnId; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getReportFileId() { return reportFileId; }
    public void setReportFileId(String reportFileId) { this.reportFileId = reportFileId; }
    public String getReportFileName() { return reportFileName; }
    public void setReportFileName(String reportFileName) { this.reportFileName = reportFileName; }
    public String getReportFileType() { return reportFileType; }
    public void setReportFileType(String reportFileType) { this.reportFileType = reportFileType; }
    public Long getReportSize() { return reportSize; }
    public void setReportSize(Long reportSize) { this.reportSize = reportSize; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

