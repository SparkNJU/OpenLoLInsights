package com.example.backend.service.chat;

import com.example.backend.enums.ChatMode;
import com.example.backend.exception.BizException;
import com.example.backend.service.chat.ChatStorageService;
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
    private final ChatStorageService chatStorageService;

    public ChatService(AgentClient agentClient, ChatStorageService chatStorageService) {
        this.agentClient = agentClient;
        this.chatStorageService = chatStorageService;
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
        final String normalizedMode = (ChatMode.from(mode) == ChatMode.REPORT) ? "report" : "simple";
        final String turnId = chatStorageService.newTurnId();

        chatStorageService.saveUserMessage(sessionId, turnId, traceId, normalizedMode, message);

        Map<String, Object> payload = buildAgentPayload(sessionId, message, mode, context, traceId);

        final StringBuilder answer = new StringBuilder();
        final Map<String, Object> reportMetaRef = new HashMap<>();
        final java.util.concurrent.atomic.AtomicBoolean saved = new java.util.concurrent.atomic.AtomicBoolean(false);

        java.lang.Runnable saveAssistantOnce = () -> {
            if (saved.compareAndSet(false, true)) {
                String fileId = (String) reportMetaRef.get("fileId");
                String fileName = (String) reportMetaRef.get("fileName");
                String fileType = (String) reportMetaRef.get("fileType");
                Long size = null;
                Object sizeObj = reportMetaRef.get("size");
                if (sizeObj instanceof Number n) size = n.longValue();

                chatStorageService.saveAssistantMessage(
                        sessionId,
                        turnId,
                        traceId,
                        normalizedMode,
                        answer.toString(),
                        fileId,
                        fileName,
                        fileType,
                        size
                );
            }
        };

        return agentClient.stream(accessToken, payload)
                .doOnNext(sse -> {
                    String ev = sse.event();
                    String data = sse.data();

                    // 兼容：Spring 解析 SSE 时 event 可能为空（被当作默认 message/data 事件）
                    // 这种情况下尝试从 data JSON 中识别 type/delta/ok 等字段。
                    if (data == null) return;

                    if ("token".equals(ev)) {
                        appendDelta(answer, data);
                        return;
                    }
                    if ("file_meta".equals(ev)) {
                        mergeJsonMap(reportMetaRef, data);
                        return;
                    }
                    if ("done".equals(ev)) {
                        saveAssistantOnce.run();
                        return;
                    }

                    // event 为空或是 data：尝试识别
                    // token: {"delta":"..."}
                    if (data.contains("\"delta\"")) {
                        appendDelta(answer, data);
                        return;
                    }
                    // done: {"ok":true,...}
                    if (data.contains("\"ok\"") && data.contains("true")) {
                        saveAssistantOnce.run();
                        return;
                    }
                    // file_meta: {"fileId":"..."...}
                    if (data.contains("\"fileId\"")) {
                        mergeJsonMap(reportMetaRef, data);
                    }
                })
                // 即使 agent 没有发 done（或被解析丢了），流结束时也兜底落库一次 assistant
                .doFinally(sig -> {
                    // 若没有任何 token，answer 可能为空；但仍然存一条 assistant，便于历史成对展示
                    saveAssistantOnce.run();
                })
                .map(sse -> ServerSentEvent.builder(sse.data())
                        .event(sse.event())
                        .id(sse.id())
                        .comment(sse.comment())
                        .build())
                .onErrorResume(e -> {
                    // SSE 场景：不要让连接直接断掉，返回一个 error 事件方便前端感知
                    String code = (e instanceof BizException) ? ((BizException) e).getCode() : "AI_SERVICE_ERROR";
                    Map<String, Object> err = new HashMap<>();
                    err.put("code", code);
                    err.put("message", e.getMessage());
                    if (e instanceof BizException be && be.getDetails() != null) {
                        err.put("details", be.getDetails());
                    }
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
        final String normalizedMode = (ChatMode.from(mode) == ChatMode.REPORT) ? "report" : "simple";
        final String turnId = chatStorageService.newTurnId();

        chatStorageService.saveUserMessage(sessionId, turnId, traceId, normalizedMode, message);
        Map<String, Object> payload = buildAgentPayload(sessionId, message, mode, context, traceId);

        Map<String, Object> res;
        try {
            res = agentClient.query(accessToken, payload);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("AI_SERVICE_ERROR", "AI 服务调用失败", Map.of("traceId", traceId, "cause", e.getMessage()));
        }

        if (res == null) {
            throw new BizException("AI_SERVICE_ERROR", "AI 服务无响应", Map.of("traceId", traceId));
        }

        // agent query 返回结构里通常有 answer / reportMeta
        String answer = res.get("answer") == null ? "" : String.valueOf(res.get("answer"));

        String fileId = null;
        String fileName = null;
        String fileType = null;
        Long size = null;
        Object rm = res.get("reportMeta");
        if (rm instanceof Map<?, ?> m) {
            Object fid = m.get("fileId");
            if (fid != null) fileId = String.valueOf(fid);
            Object fn = m.get("fileName");
            if (fn != null) fileName = String.valueOf(fn);
            Object ft = m.get("fileType");
            if (ft != null) fileType = String.valueOf(ft);
            Object sz = m.get("size");
            if (sz instanceof Number n) size = n.longValue();
        }

        chatStorageService.saveAssistantMessage(sessionId, turnId, traceId, normalizedMode, answer, fileId, fileName, fileType, size);

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

        // 对齐 Agent 设计文档：RunRequest 的必填字段是 query（不是 message）
        payload.put("query", message);

        ChatMode m = ChatMode.from(mode);
        payload.put("mode", (m == ChatMode.REPORT) ? "report" : "simple");

        payload.put("context", context == null ? Map.of() : context);

        // 预留字段：agent / reportConfig
        return payload;
    }

    private static String toJson(Object obj) {
        try {
            return com.fasterxml.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static void appendDelta(StringBuilder answer, String data) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> obj = com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readValue(data, Map.class);
            Object delta = obj.get("delta");
            if (delta != null) answer.append(delta);
        } catch (Exception ignore) {
            answer.append(data);
        }
    }

    private static void mergeJsonMap(Map<String, Object> target, String data) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> obj = com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readValue(data, Map.class);
            target.clear();
            target.putAll(obj);
        } catch (Exception ignore) {
        }
    }
}
