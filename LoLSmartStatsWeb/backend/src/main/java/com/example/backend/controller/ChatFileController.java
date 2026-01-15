package com.example.backend.controller;

import com.example.backend.entity.ChatMessage;
import com.example.backend.exception.BizException;
import com.example.backend.repository.ChatMessageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/chat/files")
public class ChatFileController {

    private final String baseUrl;
    private final String apiKey;
    private final ChatMessageRepository chatMessageRepository;

    public ChatFileController(@Value("${app.ai.base-url}") String baseUrl,
                              @Value("${app.ai.api-key}") String apiKey,
                              ChatMessageRepository chatMessageRepository) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.chatMessageRepository = chatMessageRepository;
    }

    /**
     * 下载 agent 端生成的报告文件。
     *
     * 说明：当前后端是 Spring MVC（Tomcat/Servlet），不要返回 Flux<DataBuffer>，否则可能被当作 JSON 序列化。
     * 这里用 StreamingResponseBody 将 agent 的文件流直接转发给客户端。
     */
    @GetMapping(value = "/{fileId}")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable String fileId,
                                                          @RequestParam(required = false) String sessionId,
                                                          @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth) {

        ChatMessage msg = null;
        if (sessionId != null && !sessionId.isBlank()) {
            msg = chatMessageRepository.findFirstBySessionIdAndReportFileIdIsNotNullAndReportFileId(sessionId, fileId).orElse(null);
        } else {
            msg = chatMessageRepository.findFirstByReportFileId(fileId).orElse(null);
        }

        // 本地没找到：允许下载，但尽量回写 fileId -> 最近一条 report assistant（策略B）
        if (msg == null && sessionId != null && !sessionId.isBlank()) {
            ChatMessage latest = chatMessageRepository
                    .findBySessionIdOrderByCreatedAtAsc(sessionId, org.springframework.data.domain.PageRequest.of(0, 200))
                    .getContent()
                    .stream()
                    .filter(m -> "assistant".equals(m.getRole()) && "report".equals(m.getMode()))
                    .reduce((a, b) -> b)
                    .orElse(null);

            if (latest != null && (latest.getReportFileId() == null || latest.getReportFileId().isBlank())) {
                latest.setReportFileId(fileId);
                latest.setReportFileName(fileId);
                latest.setReportFileType("markdown");
                chatMessageRepository.save(latest);
                msg = latest;
            }
        }

        String fileName = (msg != null && msg.getReportFileName() != null && !msg.getReportFileName().isBlank())
                ? msg.getReportFileName()
                : fileId;
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8);

        String url = baseUrl + "/files/" + fileId;

        StreamingResponseBody body = outputStream -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/octet-stream");
                if (apiKey != null && !apiKey.isBlank()) {
                    conn.setRequestProperty("X-AI-API-Key", apiKey);
                }
                if (auth != null && !auth.isBlank()) {
                    conn.setRequestProperty(HttpHeaders.AUTHORIZATION, auth);
                }

                int code = conn.getResponseCode();
                if (code == 404) {
                    throw new BizException("NOT_FOUND", "文件不存在");
                }
                if (code >= 400) {
                    throw new BizException("AI_SERVICE_ERROR", "下载上游失败", java.util.Map.of("status", code));
                }

                try (InputStream in = conn.getInputStream(); OutputStream out = outputStream) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) >= 0) {
                        out.write(buf, 0, n);
                    }
                    out.flush();
                }
            } finally {
                if (conn != null) conn.disconnect();
            }
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .body(body);
    }
}
