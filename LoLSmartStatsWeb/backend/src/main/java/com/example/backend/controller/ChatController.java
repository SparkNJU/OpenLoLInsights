package com.example.backend.controller;

import com.example.backend.dto.request.ChatHistoryRequest;
import com.example.backend.dto.request.ChatStreamRequest;
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
        String accessToken = "";
        // 这里无法直接拿到原始 jwt；采用安全上下文主体 userId 仍可调用 AI，但按你的 AI 文档需要 Bearer JWT。
        // 若必须透传原 jwt，请后续在过滤器里把 token 放到 request attribute，然后这里读出来。
        // 先兼容：读取请求头的 Authorization（常见做法）
        // 注意：Spring Security 已验证 token 但不会默认暴露原串
        return chatService.streamToAgent(extractBearerToken(), req.getSessionId(), req.getMessage(), req.getMode(), req.getContext());
    }

    /**
     * 2.3 非流式问答
     */
    @PostMapping("/query")
    public ApiResponse<Map<String, Object>> query(@Valid @RequestBody ChatStreamRequest req) {
        Map<String, Object> data = chatService.queryToAgent(extractBearerToken(), req.getSessionId(), req.getMessage(), req.getMode(), req.getContext());
        return ApiResponse.ok(data, TraceIdUtil.getOrCreate());
    }

    /**
     * 2.1 创建会话（可选）：当前实现仅返回一个新 sessionId，不落库。
     * 后续如需持久化历史，可在 ChatHistoryService 中落库。
     */
    @PostMapping("/sessions")
    public ApiResponse<Map<String, Object>> createSession(@RequestBody(required = false) Map<String, Object> body) {
        String title = body == null ? null : (String) body.get("title");
        Map<String, Object> data = chatHistoryService.createSession(title);
        return ApiResponse.ok(data, TraceIdUtil.getOrCreate());
    }

    /**
     * 2.4 历史消息：后端本地实现
     */
    @PostMapping("/history")
    public ApiResponse<Map<String, Object>> history(@Valid @RequestBody ChatHistoryRequest req) {
        Map<String, Object> data = chatHistoryService.history(req.getSessionId(), req.getPage(), req.getPageSize());
        return ApiResponse.ok(data, TraceIdUtil.getOrCreate());
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
