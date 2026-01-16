package com.example.backend.service.chat;

import com.example.backend.entity.ChatMessage;
import com.example.backend.entity.ChatSession;
import com.example.backend.exception.BizException;
import com.example.backend.repository.ChatMessageRepository;
import com.example.backend.repository.ChatSessionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ChatHistoryService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatSessionRepository chatSessionRepository;

    public ChatHistoryService(ChatMessageRepository chatMessageRepository, ChatSessionRepository chatSessionRepository) {
        this.chatMessageRepository = chatMessageRepository;
        this.chatSessionRepository = chatSessionRepository;
    }

    /**
     * 2.1 创建会话：落库并绑定 userId。
     */
    public Map<String, Object> createSession(String userId, String title) {
        String sessionId = "s_" + UUID.randomUUID().toString().replace("-", "");

        ChatSession s = new ChatSession();
        s.setId(sessionId);
        s.setUserId(userId);
        s.setTitle(title == null ? "" : title);
        s.setStatus("active");
        chatSessionRepository.save(s);

        Map<String, Object> data = new HashMap<>();
        data.put("sessionId", sessionId);
        data.put("title", s.getTitle());
        data.put("status", s.getStatus());
        data.put("createdAt", s.getCreatedAt() == null ? Instant.now().toString() : s.getCreatedAt().toString());
        return data;
    }

    /**
     * 会话列表：分页 + 过滤（status, from/to 时间范围），按 updatedAt 倒序。
     */
    public Map<String, Object> listSessions(String userId, String status, Instant from, Instant to, int page, int pageSize) {
        int p = Math.max(page, 1);
        int ps = Math.max(pageSize, 1);

        Page<ChatSession> sPage;
        boolean hasStatus = status != null && !status.isBlank();
        boolean hasRange = from != null && to != null;

        if (hasStatus && hasRange) {
            sPage = chatSessionRepository.findByUserIdAndStatusAndUpdatedAtBetweenOrderByUpdatedAtDesc(userId, status, from, to, PageRequest.of(p - 1, ps));
        } else if (hasStatus) {
            sPage = chatSessionRepository.findByUserIdAndStatusOrderByUpdatedAtDesc(userId, status, PageRequest.of(p - 1, ps));
        } else if (hasRange) {
            sPage = chatSessionRepository.findByUserIdAndUpdatedAtBetweenOrderByUpdatedAtDesc(userId, from, to, PageRequest.of(p - 1, ps));
        } else {
            sPage = chatSessionRepository.findByUserIdOrderByUpdatedAtDesc(userId, PageRequest.of(p - 1, ps));
        }

        List<Map<String, Object>> items = sPage.getContent().stream().map(s -> {
            Map<String, Object> it = new HashMap<>();
            it.put("sessionId", s.getId());
            it.put("title", s.getTitle());
            it.put("status", s.getStatus());
            it.put("createdAt", s.getCreatedAt() == null ? null : s.getCreatedAt().toString());
            it.put("updatedAt", s.getUpdatedAt() == null ? null : s.getUpdatedAt().toString());
            return it;
        }).collect(Collectors.toList());

        Map<String, Object> data = new HashMap<>();
        data.put("total", sPage.getTotalElements());
        data.put("page", p);
        data.put("pageSize", ps);
        data.put("items", items);
        return data;
    }

    private ChatSession requireSession(String sessionId, String userId) {
        return chatSessionRepository.findById(sessionId)
                .filter(s -> s.getUserId().equals(userId))
                .orElseThrow(() -> new BizException("NOT_FOUND", "会话不存在或无权限"));
    }

    /**
     * 2.4 历史消息：后端本地落库并分页读取（按 createdAt 升序）。
     */
    public Map<String, Object> history(String userId, String sessionId, int page, int pageSize) {
        requireSession(sessionId, userId);

        int p = Math.max(page, 1);
        int ps = Math.max(pageSize, 1);

        Page<ChatMessage> msgPage = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(
                sessionId,
                PageRequest.of(p - 1, ps)
        );

        // 额外安全：过滤掉非本人数据（避免历史数据里混入旧数据/脏数据）
        List<Map<String, Object>> items = msgPage.getContent().stream()
                .filter(m -> userId.equals(m.getUserId()))
                .map(m -> {
                    Map<String, Object> it = new HashMap<>();
                    it.put("mode", m.getMode());
                    it.put("role", m.getRole());
                    it.put("ts", m.getCreatedAt() == null ? null : m.getCreatedAt().toString());

                    boolean hasReport = "assistant".equals(m.getRole()) && m.getReportFileId() != null && !m.getReportFileId().isBlank();

                    String cleaned = cleanAssistantContent(m.getContent());

                    if (hasReport) {
                        Map<String, Object> reportMeta = new HashMap<>();
                        reportMeta.put("fileId", m.getReportFileId());
                        reportMeta.put("fileName", m.getReportFileName());
                        reportMeta.put("fileType", m.getReportFileType());
                        reportMeta.put("size", m.getReportSize());
                        it.put("reportMeta", reportMeta);
                        it.put("downloadUrl", "/api/v1/chat/files/" + m.getReportFileId() + "?sessionId=" + m.getSessionId());

                        it.put("content", (cleaned == null || cleaned.isBlank()) ? "报告已生成，可点击下载。" : "报告已生成，可点击下载。\n\n（你也可以在此处展示摘要）");
                        it.put("preview", safePreview(cleaned, 500));
                    } else {
                        it.put("content", cleaned);
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

    private static String safePreview(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen);
    }

    private static String cleanAssistantContent(String raw) {
        if (raw == null) return "";
        String r = raw.trim();
        if (r.isEmpty()) return "";

        if (!r.contains("event:") && !r.contains("data:")) {
            return r;
        }

        StringBuilder out = new StringBuilder();
        String[] parts = r.split("data:");
        for (int i = 1; i < parts.length; i++) {
            String seg = parts[i].trim();
            int end = seg.indexOf("event:");
            String json = (end >= 0 ? seg.substring(0, end) : seg).trim();
            int nl = json.indexOf('\n');
            if (nl >= 0) json = json.substring(0, nl).trim();
            if (json.isEmpty()) continue;

            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> obj = com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readValue(json, Map.class);
                Object delta = obj.get("delta");
                if (delta != null) out.append(String.valueOf(delta));
            } catch (Exception ignore) {
                out.append(json);
            }
        }

        String cleaned = out.toString().trim();
        return cleaned.isEmpty() ? r : cleaned;
    }
}
