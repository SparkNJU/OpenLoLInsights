package com.example.backend.service.chat;

import com.example.backend.entity.ChatMessage;
import com.example.backend.repository.ChatMessageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ChatHistoryService {

    private final ChatMessageRepository chatMessageRepository;

    public ChatHistoryService(ChatMessageRepository chatMessageRepository) {
        this.chatMessageRepository = chatMessageRepository;
    }

    /**
     * 2.1 创建会话：当前版本不落库，仅生成 sessionId。
     */
    public Map<String, Object> createSession(String title) {
        Map<String, Object> data = new HashMap<>();
        data.put("sessionId", "s_" + UUID.randomUUID().toString().replace("-", ""));
        data.put("title", title == null ? "" : title);
        data.put("createdAt", Instant.now().toString());
        return data;
    }

    /**
     * 2.4 历史消息：后端本地落库并分页读取。
     */
    public Map<String, Object> history(String sessionId, int page, int pageSize) {
        int p = Math.max(page, 1);
        int ps = Math.max(pageSize, 1);

        Page<ChatMessage> msgPage = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(
                sessionId,
                PageRequest.of(p - 1, ps)
        );

        List<Map<String, Object>> items = msgPage.getContent().stream().map(m -> {
            Map<String, Object> it = new HashMap<>();
            it.put("mode", m.getMode());
            it.put("role", m.getRole());
            it.put("content", m.getContent());
            it.put("ts", m.getCreatedAt() == null ? null : m.getCreatedAt().toString());

            if ("assistant".equals(m.getRole()) && m.getReportFileId() != null && !m.getReportFileId().isBlank()) {
                Map<String, Object> reportMeta = new HashMap<>();
                reportMeta.put("fileId", m.getReportFileId());
                reportMeta.put("fileName", m.getReportFileName());
                reportMeta.put("fileType", m.getReportFileType());
                reportMeta.put("size", m.getReportSize());
                it.put("reportMeta", reportMeta);
                // 前端可直接使用此链接作为下载入口（也可自行拼接）
                it.put("downloadUrl", "/api/v1/chat/files/" + m.getReportFileId() + "?sessionId=" + m.getSessionId());
            }
            return it;
        }).collect(Collectors.toList());

        Map<String, Object> data = new HashMap<>();
        data.put("total", msgPage.getTotalElements());
        data.put("page", p);
        data.put("pageSize", ps);
        data.put("items", items);
        return data;
    }
}
