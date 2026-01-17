package com.example.backend.service.chat;

import com.example.backend.exception.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
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
                .onErrorMap(this::mapAgentError);
    }

    public Map<String, Object> query(String accessToken, Map<String, Object> payload) {
        try {
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
        } catch (Exception e) {
            throw mapAgentError(e);
        }
    }

    private BizException mapAgentError(Throwable e) {
        if (e instanceof BizException be) {
            return be;
        }

        String msg = e == null ? null : e.getMessage();
        String msgSafe = (msg == null ? "" : msg);
        if (!msgSafe.isEmpty() && (msgSafe.contains("Unauthorized") || msgSafe.contains(" 401") || msgSafe.contains("401 -"))) {
            return new BizException("UNAUTHORIZED", "AI 上游鉴权失败（请检查 agent 配置的模型 API Key）", Map.of("cause", msgSafe));
        }

        if (e instanceof WebClientResponseException w) {
            String body;
            try {
                body = w.getResponseBodyAsString(StandardCharsets.UTF_8);
            } catch (Exception ex) {
                body = "";
            }

            String code;
            int status = w.getStatusCode().value();
            if (status == 401) code = "UNAUTHORIZED";
            else if (status == 403) code = "FORBIDDEN";
            else if (status == 404) code = "AI_ENDPOINT_NOT_FOUND";
            else if (status == 422) code = "AI_INVALID_ARGUMENT";
            else if (status >= 500) code = "AI_UPSTREAM_ERROR";
            else code = "AI_SERVICE_ERROR";

            return new BizException(code, "AI 服务返回错误(" + status + ")", Map.of(
                    "status", status,
                    "path", w.getRequest() != null ? String.valueOf(w.getRequest().getURI()) : "",
                    "body", body
            ));
        }

        return new BizException("AI_SERVICE_ERROR", "AI 服务调用失败", Map.of("cause", msgSafe));
    }
}
