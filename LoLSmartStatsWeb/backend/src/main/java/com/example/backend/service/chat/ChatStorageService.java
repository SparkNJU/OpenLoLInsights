package com.example.backend.service.chat;

import com.example.backend.entity.ChatMessage;
import com.example.backend.repository.ChatMessageRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ChatStorageService {

    private final ChatMessageRepository repo;

    public ChatStorageService(ChatMessageRepository repo) {
        this.repo = repo;
    }

    public String newTurnId() {
        return "turn_" + UUID.randomUUID().toString().replace("-", "");
    }

    public void saveUserMessage(String sessionId, String turnId, String traceId, String mode, String content) {
        ChatMessage m = new ChatMessage();
        m.setSessionId(sessionId);
        m.setTurnId(turnId);
        m.setTraceId(traceId);
        m.setMode(mode);
        m.setRole("user");
        m.setContent(content == null ? "" : content);
        repo.save(m);
    }

    public ChatMessage saveAssistantMessage(String sessionId, String turnId, String traceId, String mode, String content,
                                           String reportFileId, String reportFileName, String reportFileType, Long reportSize) {
        ChatMessage m = new ChatMessage();
        m.setSessionId(sessionId);
        m.setTurnId(turnId);
        m.setTraceId(traceId);
        m.setMode(mode);
        m.setRole("assistant");
        m.setContent(content == null ? "" : content);

        if (reportFileId != null && !reportFileId.isBlank()) {
            m.setReportFileId(reportFileId);
            m.setReportFileName(reportFileName == null ? reportFileId : reportFileName);
            m.setReportFileType(reportFileType == null ? "markdown" : reportFileType);
            m.setReportSize(reportSize);
        }

        return repo.save(m);
    }
}

