package com.example.backend.service.chat;

import com.example.backend.enums.ChatMode;
import com.example.backend.exception.BizException;
import com.example.backend.util.TraceIdUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

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
                                                       String userId,
                                                       String sessionId,
                                                       String message,
                                                       String mode,
                                                       Map<String, Object> context) {
        final String traceId = TraceIdUtil.getOrCreate();
        final String normalizedMode = (ChatMode.from(mode) == ChatMode.REPORT) ? "report" : "simple";
        final String turnId = chatStorageService.newTurnId();

        log.info("[chat.stream] start traceId={}, userId={}, sessionId={}, turnId={}, mode={}=>{}, msgLen={}, ctxKeys={}",
                traceId, userId, sessionId, turnId, mode, normalizedMode,
                message == null ? 0 : message.length(),
                context == null ? "[]" : context.keySet());

        chatStorageService.saveUserMessage(userId, sessionId, turnId, traceId, normalizedMode, message);

        Map<String, Object> payload = buildAgentPayload(sessionId, message, mode, context, traceId);
        log.debug("[chat.stream] payload traceId={}, turnId={}, payload={}", traceId, turnId, toJson(payload));

        final StringBuilder answer = new StringBuilder();
        final Map<String, Object> reportMetaRef = new HashMap<>();
        final java.util.concurrent.atomic.AtomicBoolean saved = new java.util.concurrent.atomic.AtomicBoolean(false);

        java.lang.Runnable saveAssistantOnce = () -> {
            mergeReportMetaFromAnswerTextIfPossible(reportMetaRef, answer.toString());

            if (saved.compareAndSet(false, true)) {
                String fileId = (String) reportMetaRef.get("fileId");
                String fileName = (String) reportMetaRef.get("fileName");
                String fileType = (String) reportMetaRef.get("fileType");
                Long size = null;
                Object sizeObj = reportMetaRef.get("size");
                if (sizeObj instanceof Number n) size = n.longValue();

                log.info("[chat.stream] saveAssistantOnce traceId={}, turnId={}, answerLen={}, reportMetaKeys={}, fileId={}",
                        traceId, turnId, answer.length(), reportMetaRef.keySet(), fileId);

                chatStorageService.saveAssistantMessage(
                        userId,
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
            } else {
                log.debug("[chat.stream] saveAssistantOnce skipped(already saved) traceId={}, turnId={}", traceId, turnId);
            }
        };

        // 新增：在收到 file_meta 时，把元数据补写回“同一轮 turnId 的 assistant 消消息”
        java.util.function.Consumer<String> persistReportMetaToDb = (fileMetaJson) -> {
            try {
                // 兼容：有些上游把 SSE 原始块（含 "event:"/"data:"）原样塞进 data
                String extracted = extractJsonFromPossiblyRawSseBlock(fileMetaJson);

                Map<String, Object> tmp = new HashMap<>();
                mergeReportMetaFromAnyJson(tmp, extracted);
                if (tmp.get("fileId") == null) {
                    log.warn("[chat.stream] file_meta parsed but missing fileId traceId={}, turnId={}, data={}", traceId, turnId, safeClip(fileMetaJson, 500));
                    return;
                }

                String fileId = String.valueOf(tmp.get("fileId"));
                String fileName = tmp.get("fileName") == null ? fileId : String.valueOf(tmp.get("fileName"));
                String fileType = tmp.get("fileType") == null ? "markdown" : String.valueOf(tmp.get("fileType"));
                Long size = null;
                Object sz = tmp.get("size");
                if (sz instanceof Number n) size = n.longValue();

                log.info("[chat.stream] persist file_meta traceId={}, turnId={}, fileId={}, fileName={}, fileType={}, size={}",
                        traceId, turnId, fileId, fileName, fileType, size);

                chatStorageService.upsertAssistantReportMeta(userId, sessionId, turnId, traceId, normalizedMode, fileId, fileName, fileType, size);
            } catch (Exception e) {
                log.warn("[chat.stream] persist file_meta error traceId={}, turnId={}, msg={}, data={}",
                        traceId, turnId, e.getMessage(), safeClip(fileMetaJson, 500), e);
            }
        };

        final java.util.concurrent.atomic.AtomicLong eventSeq = new java.util.concurrent.atomic.AtomicLong(0);

        return agentClient.stream(accessToken, payload)
                .doOnNext(sse -> {
                    long seq = eventSeq.incrementAndGet();
                    String ev = sse.event();
                    String data = sse.data();
                    if (data == null) {
                        log.debug("[chat.stream] sse#{} traceId={}, turnId={}, event={}, data=NULL", seq, traceId, turnId, ev);
                        return;
                    }

                    // 避免 token 过多刷屏：仅 debug
                    if (!"token".equals(ev)) {
                        log.debug("[chat.stream] sse#{} traceId={}, turnId={}, event={}, data={}",
                                seq, traceId, turnId, ev, safeClip(data, 600));
                    }

                    if ("token".equals(ev)) {
                        appendDelta(answer, data);
                        return;
                    }

                    if ("file_meta".equals(ev)) {
                        mergeReportMetaFromAnyJson(reportMetaRef, data);
                        log.info("[chat.stream] got file_meta traceId={}, turnId={}, reportMetaKeys={}, fileId={}",
                                traceId, turnId, reportMetaRef.keySet(), reportMetaRef.get("fileId"));
                        persistReportMetaToDb.accept(data);
                        return;
                    }

                    if ("done".equals(ev)) {
                        mergeReportMetaFromDoneIfPossible(reportMetaRef, data);
                        log.info("[chat.stream] got done traceId={}, turnId={}, reportMetaKeys={}, fileId={}",
                                traceId, turnId, reportMetaRef.keySet(), reportMetaRef.get("fileId"));
                        saveAssistantOnce.run();
                        return;
                    }

                    if (looksLikeDeltaJson(data)) {
                        appendDelta(answer, data);
                        return;
                    }

                    if (looksLikeReportMetaJson(data)) {
                        mergeReportMetaFromAnyJson(reportMetaRef, data);
                        log.info("[chat.stream] inferred reportMeta from data traceId={}, turnId={}, keys={}, fileId={}",
                                traceId, turnId, reportMetaRef.keySet(), reportMetaRef.get("fileId"));
                        // 推断到 meta 也立刻尝试落库（如果解析出了 fileId）
                        persistReportMetaToDb.accept(data);
                        return;
                    }

                    if (looksLikeDoneJson(data)) {
                        mergeReportMetaFromDoneIfPossible(reportMetaRef, data);
                        log.info("[chat.stream] inferred done from data traceId={}, turnId={}, keys={}, fileId={}",
                                traceId, turnId, reportMetaRef.keySet(), reportMetaRef.get("fileId"));
                        saveAssistantOnce.run();
                    }
                })
                .doFinally(sig -> {
                    log.info("[chat.stream] finally traceId={}, turnId={}, signal={}, events={}, answerLen={}, fileId={}",
                            traceId, turnId, sig, eventSeq.get(), answer.length(), reportMetaRef.get("fileId"));
                    saveAssistantOnce.run();
                })
                .map(sse -> ServerSentEvent.builder(sse.data())
                        .event(sse.event())
                        .id(sse.id())
                        .comment(sse.comment())
                        .build())
                .onErrorResume(e -> {
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
                                           String userId,
                                           String sessionId,
                                           String message,
                                           String mode,
                                           Map<String, Object> context) {
        final String traceId = TraceIdUtil.getOrCreate();
        final String normalizedMode = (ChatMode.from(mode) == ChatMode.REPORT) ? "report" : "simple";
        final String turnId = chatStorageService.newTurnId();

        chatStorageService.saveUserMessage(userId, sessionId, turnId, traceId, normalizedMode, message);
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

        String answer = res.get("answer") == null ? "" : String.valueOf(res.get("answer"));

        // 兼容：有些 agent 会把 reportMeta 放在 answer 最终 JSON 字符串里
        Map<String, Object> reportMeta = null;
        if ((ChatMode.from(mode) == ChatMode.REPORT) && answer != null && answer.trim().startsWith("{")) {
            reportMeta = tryExtractReportMetaFromJsonString(answer);
        }

        // 先从标准字段读取
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

        // 再从 answer JSON 内兜底
        if (fileId == null && reportMeta != null) {
            Object fid = reportMeta.get("fileId");
            if (fid != null) fileId = String.valueOf(fid);
            Object fn = reportMeta.get("fileName");
            if (fn != null) fileName = String.valueOf(fn);
            Object ft = reportMeta.get("fileType");
            if (ft != null) fileType = String.valueOf(ft);
            Object sz = reportMeta.get("size");
            if (sz instanceof Number n) size = n.longValue();
        }

        chatStorageService.saveAssistantMessage(userId, sessionId, turnId, traceId, normalizedMode, answer, fileId, fileName, fileType, size);

        if (!res.containsKey("traceId")) res.put("traceId", traceId);
        if (!res.containsKey("sessionId")) res.put("sessionId", sessionId);
        if (!res.containsKey("startedAt")) res.put("startedAt", Instant.now().toString());
        return res;
    }

    private static boolean looksLikeDeltaJson(String data) {
        return data != null && data.contains("\"delta\"");
    }

    private static boolean looksLikeDoneJson(String data) {
        return data != null && data.contains("\"ok\"") && data.contains("true");
    }

    private static boolean looksLikeReportMetaJson(String data) {
        if (data == null) return false;
        return data.contains("\"fileId\"") || data.contains("\"reportMeta\"");
    }

    /**
     * 尝试从任意 JSON 里提取 reportMeta：兼容多种格式（平铺/嵌套/snake_case）。
     */
    private static void mergeReportMetaFromAnyJson(Map<String, Object> target, String data) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> obj = com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readValue(data, Map.class);

            Map<String, Object> rm = extractReportMetaObject(obj);
            if (rm != null && !rm.isEmpty()) {
                target.clear();
                target.putAll(rm);
            }
        } catch (Exception e) {
            log.debug("[chat.stream] mergeReportMetaFromAnyJson parse failed: {} data={}", e.getMessage(), safeClip(data, 500));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractReportMetaObject(Map<String, Object> obj) {
        if (obj == null) return null;

        // 1) 标准：{"reportMeta":{...}}
        Object reportMeta = obj.get("reportMeta");
        if (reportMeta instanceof Map<?, ?> m) {
            return normalizeMetaMap((Map<String, Object>) m);
        }

        // 2) file_meta 平铺：{fileId,fileName,...} 或 snake_case
        Map<String, Object> flat = normalizeMetaMap(obj);
        if (flat.get("fileId") != null) return flat;

        // 3) 常见嵌套：{"data":{...}} / {"result":{...}} / {"payload":{...}}
        String[] candidates = {"data", "result", "payload", "meta", "file_meta"};
        for (String k : candidates) {
            Object inner = obj.get(k);
            if (inner instanceof Map<?, ?> m2) {
                Map<String, Object> innerMap = (Map<String, Object>) m2;
                Map<String, Object> r1 = extractReportMetaObject(innerMap);
                if (r1 != null && r1.get("fileId") != null) return r1;
            }
        }

        return null;
    }

    private static Map<String, Object> normalizeMetaMap(Map<String, Object> in) {
        Map<String, Object> out = new HashMap<>();
        if (in == null) return out;

        // 兼容 snake_case
        Object fileId = firstNonNull(in.get("fileId"), in.get("file_id"), in.get("id"));
        Object fileName = firstNonNull(in.get("fileName"), in.get("file_name"), in.get("name"));
        Object fileType = firstNonNull(in.get("fileType"), in.get("file_type"), in.get("type"));
        Object size = firstNonNull(in.get("size"), in.get("fileSize"), in.get("file_size"));

        if (fileId != null) out.put("fileId", fileId);
        if (fileName != null) out.put("fileName", fileName);
        if (fileType != null) out.put("fileType", fileType);
        if (size != null) out.put("size", size);

        // 其他字段也保留（便于排查）
        for (Map.Entry<String, Object> e : in.entrySet()) {
            String k = e.getKey();
            if (k == null) continue;
            if (out.containsKey(k)) continue;
            out.put(k, e.getValue());
        }
        return out;
    }

    private static Object firstNonNull(Object... xs) {
        if (xs == null) return null;
        for (Object x : xs) if (x != null) return x;
        return null;
    }

    private static void mergeReportMetaFromDoneIfPossible(Map<String, Object> target, String doneData) {
        if (doneData == null) return;
        if (!looksLikeReportMetaJson(doneData)) return;
        mergeReportMetaFromAnyJson(target, doneData);
    }

    /**
     * 有些实现会把 fileId 等元数据放在最终 answer 的 JSON 里，而不是单独事件。
     * 这里仅在 answer 本身看起来是 JSON 时尝试解析。
     */
    private static void mergeReportMetaFromAnswerTextIfPossible(Map<String, Object> target, String answerText) {
        Map<String, Object> rm = tryExtractReportMetaFromJsonString(answerText);
        if (rm != null && !rm.isEmpty() && (rm.get("fileId") != null)) {
            target.clear();
            target.putAll(rm);
        }
    }

    private static Map<String, Object> tryExtractReportMetaFromJsonString(String jsonText) {
        if (jsonText == null) return null;
        String s = jsonText.trim();
        if (!s.startsWith("{")) return null;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> obj = com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readValue(s, Map.class);
            Object rm = obj.get("reportMeta");
            if (rm instanceof Map<?, ?> m) {
                Map<String, Object> out = new HashMap<>();
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    if (e.getKey() != null) out.put(String.valueOf(e.getKey()), e.getValue());
                }
                return out;
            }
            // 若没有 reportMeta，直接平铺且带 fileId 也认为是 meta
            if (obj.containsKey("fileId")) return obj;
        } catch (Exception ignore) {
        }
        return null;
    }

    private Map<String, Object> buildAgentPayload(String sessionId,
                                                  String message,
                                                  String mode,
                                                  Map<String, Object> context,
                                                  String traceId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sessionId", sessionId);
        payload.put("traceId", traceId);
        payload.put("query", message);

        ChatMode m = ChatMode.from(mode);
        payload.put("mode", (m == ChatMode.REPORT) ? "report" : "simple");

        payload.put("context", context == null ? Map.of() : context);
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
        } catch (Exception e) {
            // 解析失败时把原始 data 追加入 answer，同时输出 debug 便于定位 agent 格式变更
            log.debug("[chat.stream] appendDelta parse failed: {} data={}", e.getMessage(), safeClip(data, 300));
            answer.append(data);
        }
    }

    private static String safeClip(String s, int max) {
        if (s == null) return null;
        if (s.length() <= max) return s;
        return s.substring(0, max) + "...";
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

    /**
     * 从可能包含原始 SSE 块的字符串中提取 JSON：
     * - 输入可能是纯 JSON：{"fileId":...}
     * - 也可能是："event: file_meta\ndata: {...}"（甚至 data 后续多行）
     */
    private static String extractJsonFromPossiblyRawSseBlock(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.startsWith("{")) return t;

        int dataIdx = t.indexOf("data:");
        if (dataIdx < 0) return t;

        String after = t.substring(dataIdx + "data:".length()).trim();
        // data: 后可能还有换行（严格 SSE 是 data:xxx\n data:yyy），这里简单拼接并截取从第一个 '{' 开始
        int jsonStart = after.indexOf('{');
        if (jsonStart >= 0) {
            return after.substring(jsonStart).trim();
        }
        return after;
    }
}
