package com.example.backend.service.chat;

import com.example.backend.enums.ChatMode;
import com.example.backend.exception.BizException;
import com.example.backend.util.TraceIdUtil;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class ChatService {

    private final AgentClient agentClient;

    public ChatService(AgentClient agentClient) {
        this.agentClient = agentClient;
    }

    /**
     * 后端对前端的 /chat/stream：
     * - 后端生成 traceId
     * - 组装 AI 端规范的 payload（sessionId/traceId/message/mode/context/...）
     * - 订阅 AI 端 SSE 并原样转发 event/data
     */
    public Flux<ServerSentEvent<String>> streamToAgent(String accessToken,
                                                       String sessionId,
                                                       String message,
                                                       String mode,
                                                       Map<String, Object> context) {
        final String traceId = TraceIdUtil.getOrCreate();

        Map<String, Object> payload = buildAgentPayload(sessionId, message, mode, context, traceId);

        return agentClient.stream(accessToken, payload)
                .map(sse -> ServerSentEvent.builder(sse.data())
                        .event(sse.event())
                        .id(sse.id())
                        .comment(sse.comment())
                        .build())
                .onErrorResume(e -> {
                    Map<String, Object> err = new HashMap<>();
                    err.put("code", (e instanceof BizException) ? ((BizException) e).getCode() : "AI_SERVICE_ERROR");
                    err.put("message", e.getMessage());
                    err.put("traceId", traceId);
                    err.put("retryable", true);
                    return Flux.just(ServerSentEvent.builder(toJson(err)).event("error").build());
                });
    }

    /**
     * 后端对前端的 /chat/query：非流式。
     */
    public Map<String, Object> queryToAgent(String accessToken,
                                           String sessionId,
                                           String message,
                                           String mode,
                                           Map<String, Object> context) {
        final String traceId = TraceIdUtil.getOrCreate();
        Map<String, Object> payload = buildAgentPayload(sessionId, message, mode, context, traceId);

        Map<String, Object> res = agentClient.query(accessToken, payload);
        if (res == null) {
            throw new BizException("AI_SERVICE_ERROR", "AI 服务无响应", Map.of("traceId", traceId));
        }
        // 兜底补齐关键字段
        if (!res.containsKey("traceId")) res.put("traceId", traceId);
        if (!res.containsKey("sessionId")) res.put("sessionId", sessionId);
        if (!res.containsKey("startedAt")) res.put("startedAt", Instant.now().toString());
        return res;
    }

    private Map<String, Object> buildAgentPayload(String sessionId,
                                                  String message,
                                                  String mode,
                                                  Map<String, Object> context,
                                                  String traceId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sessionId", sessionId);
        payload.put("traceId", traceId);
        payload.put("message", message);

        // AI 端只认识 simple / report。api.md 里 data+analysis 视为 simple。
        ChatMode m = ChatMode.from(mode);
        payload.put("mode", (m == ChatMode.REPORT) ? "report" : "simple");

        payload.put("context", context == null ? Map.of() : context);

        // 可扩展字段：dbQueryResult / reportConfig
        return payload;
    }

    private static String toJson(Object obj) {
        try {
            return com.fasterxml.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}

