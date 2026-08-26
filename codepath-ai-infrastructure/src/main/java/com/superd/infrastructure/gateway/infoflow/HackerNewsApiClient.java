package com.superd.infrastructure.gateway.infoflow;

import com.alibaba.fastjson.JSON;
import com.superd.infrastructure.config.InfoFlowProperties;
import com.superd.infrastructure.gateway.dto.HackerNewsItemDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

@Slf4j
@Component
public class HackerNewsApiClient {
    private final HttpClient httpClient;
    private final InfoFlowProperties properties;

    public HackerNewsApiClient(InfoFlowProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getHackerNews().getConnectTimeoutSeconds()))
                .build();
    }
    public List<Long> topStoryIds() {
        List<?> values = get(properties.getHackerNews().getApiBaseUrl() + "/topstories.json", List.class);
        if (values == null) {
            return Collections.emptyList();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(value -> value instanceof Number
                        ? ((Number) value).longValue()
                        : Long.valueOf(String.valueOf(value)))
                .toList();
    }
    public HackerNewsItemDTO item(long id) { return get(properties.getHackerNews().getApiBaseUrl() + "/item/" + id + ".json", HackerNewsItemDTO.class); }
    private <T> T get(String url, Class<T> type) {
        int maxAttempts = Math.max(1, properties.getHackerNews().getMaxRetries() + 1);
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(properties.getHackerNews().getRequestTimeoutSeconds()))
                        .header("User-Agent", "codepath-ai-infoflow")
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() / 100 != 2) {
                    throw new IllegalStateException("Hacker News API returned HTTP " + response.statusCode());
                }
                return JSON.parseObject(response.body(), type);
            } catch (Exception exception) {
                if (attempt == maxAttempts) {
                    throw new IllegalStateException("Hacker News API request failed", exception);
                }
                log.warn("InfoFlow hacker_news 外部请求失败，第 {}/{} 次尝试，准备重试", attempt, maxAttempts);
                sleepBeforeRetry();
            }
        }
        throw new IllegalStateException("Hacker News API request failed");
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(Math.max(0, properties.getHackerNews().getRetryDelayMillis()));
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Hacker News API retry interrupted", interruptedException);
        }
    }
}
