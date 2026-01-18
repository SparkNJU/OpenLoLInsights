package com.example.backend.service.chat;

import com.example.backend.entity.ChatMessage;
import com.example.backend.entity.ChatSession;
import com.example.backend.exception.BizException;
import com.example.backend.repository.ChatMessageRepository;
import com.example.backend.repository.ChatSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ChatStorageService {

    private static final Logger log = LoggerFactory.getLogger(ChatStorageService.class);

    private final ChatMessageRepository repo;
    private final ChatSessionRepository chatSessionRepository;

    public ChatStorageService(ChatMessageRepository repo, ChatSessionRepository chatSessionRepository) {
        this.repo = repo;
        this.chatSessionRepository = chatSessionRepository;
    }

    public String newTurnId() {
        return "turn_" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 获取会话，支持匿名用户创建临时会话
     */
    private ChatSession requireSession(String sessionId, String userId) {
        // 如果是匿名用户
        if (userId.startsWith("anonymous_")) {
            log.debug("[chat.db] Anonymous user access: userId={}, sessionId={}", userId, sessionId);

            return chatSessionRepository.findById(sessionId)
                    .orElseGet(() -> {
                        // 创建临时会话
                        log.info("[chat.db] Creating temporary session for anonymous user: sessionId={}, userId={}",
                                sessionId, userId);

                        ChatSession s = new ChatSession();
                        s.setId(sessionId);
                        s.setUserId(userId);
                        s.setTitle("临时会话");
                        s.setStatus("active");
                        s.setCreatedAt(java.time.Instant.now());
                        s.setUpdatedAt(java.time.Instant.now());

                        return chatSessionRepository.save(s);
                    });
        }

        // 已登录用户，保持原来的检查
        return chatSessionRepository.findById(sessionId)
                .filter(s -> s.getUserId().equals(userId))
                .orElseThrow(() -> new BizException("NOT_FOUND", "会话不存在或无权限"));
    }

    public void saveUserMessage(String userId, String sessionId, String turnId, String traceId, String mode,
            String content) {
        ChatSession s = requireSession(sessionId, userId);

        ChatMessage m = new ChatMessage();
        m.setUserId(userId);
        m.setSessionId(sessionId);
        m.setTurnId(turnId);
        m.setTraceId(traceId);
        m.setMode(mode);
        m.setRole("user");
        m.setStatus(s.getStatus());
        m.setContent(content == null ? "" : content);
        repo.save(m);

        // 触发 @PreUpdate
        s.setUpdatedAt(java.time.Instant.now());
        chatSessionRepository.save(s);
    }

    public ChatMessage saveAssistantMessage(String userId,
            String sessionId,
            String turnId,
            String traceId,
            String mode,
            String content,
            String reportFileId,
            String reportFileName,
            String reportFileType,
            Long reportSize) {
        ChatSession s = requireSession(sessionId, userId);

        // 关键：同一轮(turnId)只保留一条 assistant 记录。
        ChatMessage m = repo.findFirstBySessionIdAndTurnIdAndRole(sessionId, turnId, "assistant").orElse(null);
        if (m == null) {
            m = new ChatMessage();
            m.setUserId(userId);
            m.setSessionId(sessionId);
            m.setTurnId(turnId);
            m.setTraceId(traceId);
            m.setMode(mode);
            m.setRole("assistant");
            m.setStatus(s.getStatus());
            log.info("[chat.db] saveAssistantMessage create new assistant row traceId={}, sessionId={}, turnId={}",
                    traceId, sessionId, turnId);
        } else {
            log.info(
                    "[chat.db] saveAssistantMessage update existing assistant row traceId={}, sessionId={}, turnId={}, messageId={}, existingFileId={}",
                    traceId, sessionId, turnId, m.getId(), m.getReportFileId());
        }

        // 更新正文
        m.setContent(content == null ? "" : content);

        // 回写 file meta
        if (reportFileId != null && !reportFileId.isBlank()) {
            m.setReportFileId(reportFileId);
            m.setReportFileName(reportFileName == null ? reportFileId : reportFileName);
            m.setReportFileType(reportFileType == null ? "markdown" : reportFileType);
            m.setReportSize(reportSize);
        }

        ChatMessage saved = repo.save(m);

        log.info(
                "[chat.db] saveAssistantMessage saved traceId={}, sessionId={}, turnId={}, mode={}, messageId={}, answerLen={}, reportFileId={}",
                traceId, sessionId, turnId, mode, saved.getId(), (content == null ? 0 : content.length()),
                saved.getReportFileId());

        s.setUpdatedAt(java.time.Instant.now());
        chatSessionRepository.save(s);

        return saved;
    }

    public void upsertAssistantReportMeta(String userId,
            String sessionId,
            String turnId,
            String traceId,
            String mode,
            String reportFileId,
            String reportFileName,
            String reportFileType,
            Long reportSize) {
        if (reportFileId == null || reportFileId.isBlank()) {
            log.warn("[chat.db] upsertAssistantReportMeta skipped(blank fileId) traceId={}, sessionId={}, turnId={}",
                    traceId, sessionId, turnId);
            return;
        }

        ChatSession s = requireSession(sessionId, userId);

        ChatMessage m = repo.findFirstBySessionIdAndTurnIdAndRole(sessionId, turnId, "assistant").orElse(null);
        if (m == null) {
            log.info(
                    "[chat.db] upsertAssistantReportMeta assistant row not found, create placeholder. traceId={}, sessionId={}, turnId={}",
                    traceId, sessionId, turnId);
            m = new ChatMessage();
            m.setUserId(userId);
            m.setSessionId(sessionId);
            m.setTurnId(turnId);
            m.setTraceId(traceId);
            m.setMode(mode);
            m.setRole("assistant");
            m.setStatus(s.getStatus());
            m.setContent("");
        } else {
            log.info(
                    "[chat.db] upsertAssistantReportMeta hit existing assistant row. traceId={}, sessionId={}, turnId={}, messageId={} ",
                    traceId, sessionId, turnId, m.getId());
        }

        m.setReportFileId(reportFileId);
        m.setReportFileName(reportFileName == null ? reportFileId : reportFileName);
        m.setReportFileType(reportFileType == null ? "markdown" : reportFileType);
        m.setReportSize(reportSize);

        ChatMessage saved = repo.save(m);
        log.info(
                "[chat.db] upsertAssistantReportMeta saved traceId={}, sessionId={}, turnId={}, messageId={}, fileId={}, fileName={}, fileType={}, size={}",
                traceId, sessionId, turnId, saved.getId(), reportFileId, saved.getReportFileName(),
                saved.getReportFileType(), saved.getReportSize());

        s.setUpdatedAt(java.time.Instant.now());
        chatSessionRepository.save(s);
    }
}