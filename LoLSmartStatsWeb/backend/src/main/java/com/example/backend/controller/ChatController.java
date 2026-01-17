package com.example.backend.controller;

import com.example.backend.dto.request.ChatHistoryRequest;
import com.example.backend.dto.request.ChatSessionCreateRequest;
import com.example.backend.dto.request.ChatSessionListRequest;
import com.example.backend.dto.request.ChatStreamRequest;
import com.example.backend.exception.BizException;
import com.example.backend.service.chat.ChatHistoryService;
import com.example.backend.service.chat.ChatService;
import com.example.backend.util.TraceIdUtil;
import com.example.backend.vo.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chatService;
    private final ChatHistoryService chatHistoryService;

    public ChatController(ChatService chatService, ChatHistoryService chatHistoryService) {
        this.chatService = chatService;
        this.chatHistoryService = chatHistoryService;
    }

    /**
     * 2.2 流式问答：SSE over POST
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@Valid @RequestBody ChatStreamRequest req, Authentication authentication) {
        String userId = currentUserId(authentication);
        return chatService.streamToAgent(extractBearerToken(), userId, req.getSessionId(), req.getMessage(), req.getMode(), req.getContext());
    }

    /**
     * 2.3 非流式问答
     */
    @PostMapping("/query")
    public ApiResponse<Map<String, Object>> query(@Valid @RequestBody ChatStreamRequest req, Authentication authentication) {
        String userId = currentUserId(authentication);
        Map<String, Object> data = chatService.queryToAgent(extractBearerToken(), userId, req.getSessionId(), req.getMessage(), req.getMode(), req.getContext());
        return ApiResponse.ok(data, TraceIdUtil.getOrCreate());
    }

    /**
     * 2.1 创建会话：落库并绑定当前用户。
     */
    @PostMapping("/sessions")
    public ApiResponse<Map<String, Object>> createSession(@RequestBody(required = false) ChatSessionCreateRequest body,
                                                          Authentication authentication) {
        String userId = currentUserId(authentication);
        String title = body == null ? null : body.getTitle();
        Map<String, Object> data = chatHistoryService.createSession(userId, title);
        return ApiResponse.ok(data, TraceIdUtil.getOrCreate());
    }

    /**
     * 会话列表查询：分页 + 过滤（status、from/to），按 updatedAt 倒序。
     */
    @PostMapping("/sessions/list")
    public ApiResponse<Map<String, Object>> listSessions(@Valid @RequestBody(required = false) ChatSessionListRequest req,
                                                         Authentication authentication) {
        if (req == null) {
            throw new BizException("INVALID_ARGUMENT", "缺少请求体，请传 page/pageSize，可选 status/from/to");
        }
        String userId = currentUserId(authentication);

        Instant from = null;
        Instant to = null;
        try {
            if (req.getFrom() != null && !req.getFrom().isBlank()) from = Instant.parse(req.getFrom());
            if (req.getTo() != null && !req.getTo().isBlank()) to = Instant.parse(req.getTo());
        } catch (Exception e) {
            throw new BizException("INVALID_ARGUMENT", "from/to 必须是 ISO-8601 时间（如 2026-01-15T00:00:00Z）");
        }

        Map<String, Object> data = chatHistoryService.listSessions(userId, req.getStatus(), from, to, req.getPage(), req.getPageSize());
        return ApiResponse.ok(data, TraceIdUtil.getOrCreate());
    }

    /**
     * 2.4 历史消息：后端本地实现
     */
    @PostMapping(value = "/history", consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_OCTET_STREAM_VALUE})
    public ApiResponse<Map<String, Object>> history(@Valid @RequestBody(required = false) ChatHistoryRequest req,
                                                    Authentication authentication) {
        if (req == null) {
            throw new BizException("INVALID_ARGUMENT", "缺少请求体，请传 sessionId/page/pageSize");
        }
        String userId = currentUserId(authentication);
        Map<String, Object> data = chatHistoryService.history(userId, req.getSessionId(), req.getPage(), req.getPageSize());
        return ApiResponse.ok(data, TraceIdUtil.getOrCreate());
    }

    /**
     * 便于调试：GET 方式查历史（避免 Content-Type 误配）
     */
    @GetMapping("/history")
    public ApiResponse<Map<String, Object>> historyGet(@RequestParam String sessionId,
                                                       @RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int pageSize,
                                                       Authentication authentication) {
        String userId = currentUserId(authentication);
        Map<String, Object> data = chatHistoryService.history(userId, sessionId, page, pageSize);
        return ApiResponse.ok(data, TraceIdUtil.getOrCreate());
    }

    private static String currentUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new BizException("UNAUTHORIZED", "未登录");
        }
        return authentication.getName();
    }

    // 从当前 HTTP 请求里拿 Authorization: Bearer xxx
    private String extractBearerToken() {
        org.springframework.web.context.request.ServletRequestAttributes attrs =
                (org.springframework.web.context.request.ServletRequestAttributes) org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attrs == null) return "";
        String auth = attrs.getRequest().getHeader(org.springframework.http.HttpHeaders.AUTHORIZATION);
        if (auth == null) return "";
        if (auth.startsWith("Bearer ")) return auth.substring("Bearer ".length()).trim();
        return auth;
    }
}
