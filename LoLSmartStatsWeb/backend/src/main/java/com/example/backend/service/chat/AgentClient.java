package com.example.backend.service.chat;

import com.example.backend.exception.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Flux;

import java.util.Map;

@Component
public class AgentClient {

    private final WebClient webClient;
    private final String apiKey;

    public AgentClient(
            WebClient.Builder builder,
            @Value("${app.ai.base-url}") String baseUrl,
            @Value("${app.ai.api-key}") String apiKey
    ) {
        this.webClient = builder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
    }

    public Flux<ServerSentEvent<String>> stream(String accessToken, Map<String, Object> payload) {
        return webClient.post()
                .uri("/chat/stream")
                .header("X-AI-API-Key", apiKey)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .body(BodyInserters.fromValue(payload))
                .retrieve()
                .bodyToFlux(new org.springframework.core.ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .onErrorResume(e -> Flux.error(new BizException("INTERNAL_ERROR", "AI 服务调用失败", Map.of("cause", e.getMessage()))));
    }

    public Map<String, Object> query(String accessToken, Map<String, Object> payload) {
        return webClient.post()
                .uri("/chat/query")
                .header("X-AI-API-Key", apiKey)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(payload))
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .block();
    }
}

