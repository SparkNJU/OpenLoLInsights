package com.example.backend.service.chat;

import com.example.backend.exception.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ChatHistoryService {

    private final WebClient webClient;
    private final String apiKey;

    public ChatHistoryService(WebClient.Builder builder,
                              @Value("${app.ai.base-url}") String baseUrl,
                              @Value("${app.ai.api-key}") String apiKey) {
        this.webClient = builder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
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
     * 2.4 历史消息：调用 AI 端 /api/v1/ai/chat/history 透传。
     */
    public Map<String, Object> historyFromAgent(String accessToken, String sessionId, int page, int pageSize) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sessionId", sessionId);
        payload.put("page", page);
        payload.put("pageSize", pageSize);

        Map<String, Object> res = webClient.post()
                .uri("/chat/history")
                .header("X-AI-API-Key", apiKey)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(payload))
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        if (res == null) {
            throw new BizException("AI_SERVICE_ERROR", "AI 历史接口无响应");
        }
        return res;
    }
}
