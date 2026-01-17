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

    private ChatSession requireSession(String sessionId, String userId) {
        return chatSessionRepository.findById(sessionId)
                .filter(s -> s.getUserId().equals(userId))
                .orElseThrow(() -> new BizException("NOT_FOUND", "会话不存在或无权限"));
    }

    public void saveUserMessage(String userId, String sessionId, String turnId, String traceId, String mode, String content) {
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
        // file_meta 可能先到并创建了占位行，这里需要对同一行进行更新补全 content。
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
            log.info("[chat.db] saveAssistantMessage create new assistant row traceId={}, sessionId={}, turnId={}", traceId, sessionId, turnId);
        } else {
            // 若本轮先收到了 file_meta，这里会命中占位行
            log.info("[chat.db] saveAssistantMessage update existing assistant row traceId={}, sessionId={}, turnId={}, messageId={}, existingFileId={}",
                    traceId, sessionId, turnId, m.getId(), m.getReportFileId());
        }

        // 更新正文（占位行 content="" 会被补全）
        m.setContent(content == null ? "" : content);

        // 回写 file meta：如果调用方传了就覆盖；否则保留之前 upsert 写入的值
        if (reportFileId != null && !reportFileId.isBlank()) {
            m.setReportFileId(reportFileId);
            m.setReportFileName(reportFileName == null ? reportFileId : reportFileName);
            m.setReportFileType(reportFileType == null ? "markdown" : reportFileType);
            m.setReportSize(reportSize);
        }

        ChatMessage saved = repo.save(m);

        log.info("[chat.db] saveAssistantMessage saved traceId={}, sessionId={}, turnId={}, mode={}, messageId={}, answerLen={}, reportFileId={}",
                traceId, sessionId, turnId, mode, saved.getId(), (content == null ? 0 : content.length()), saved.getReportFileId());

        s.setUpdatedAt(java.time.Instant.now());
        chatSessionRepository.save(s);

        return saved;
    }

    /**
     * 流式 report 场景：file_meta 事件可能先到，也可能晚到。
     * 这里按 (sessionId, turnId, role=assistant) 定位同一轮 assistant 记录并回写 report_file_*。
     */
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
            log.warn("[chat.db] upsertAssistantReportMeta skipped(blank fileId) traceId={}, sessionId={}, turnId={}", traceId, sessionId, turnId);
            return;
        }

        ChatSession s = requireSession(sessionId, userId);

        ChatMessage m = repo.findFirstBySessionIdAndTurnIdAndRole(sessionId, turnId, "assistant").orElse(null);
        if (m == null) {
            log.info("[chat.db] upsertAssistantReportMeta assistant row not found, create placeholder. traceId={}, sessionId={}, turnId={}",
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
            log.info("[chat.db] upsertAssistantReportMeta hit existing assistant row. traceId={}, sessionId={}, turnId={}, messageId={} ",
                    traceId, sessionId, turnId, m.getId());
        }

        m.setReportFileId(reportFileId);
        m.setReportFileName(reportFileName == null ? reportFileId : reportFileName);
        m.setReportFileType(reportFileType == null ? "markdown" : reportFileType);
        m.setReportSize(reportSize);

        ChatMessage saved = repo.save(m);
        log.info("[chat.db] upsertAssistantReportMeta saved traceId={}, sessionId={}, turnId={}, messageId={}, fileId={}, fileName={}, fileType={}, size={}",
                traceId, sessionId, turnId, saved.getId(), reportFileId, saved.getReportFileName(), saved.getReportFileType(), saved.getReportSize());

        s.setUpdatedAt(java.time.Instant.now());
        chatSessionRepository.save(s);
    }
}
