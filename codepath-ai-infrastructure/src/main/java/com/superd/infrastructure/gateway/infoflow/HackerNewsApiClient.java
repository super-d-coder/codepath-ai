package com.superd.infrastructure.gateway.infoflow;

import com.alibaba.fastjson.JSON;
import com.superd.infrastructure.config.InfoFlowProperties;
import com.superd.infrastructure.gateway.dto.HackerNewsItemDTO;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

@Component
public class HackerNewsApiClient {
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final InfoFlowProperties properties;
    public HackerNewsApiClient(InfoFlowProperties properties) { this.properties = properties; }
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
    private <T> T get(String url, Class<T> type) { try { HttpResponse<String> r=httpClient.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).GET().build(), HttpResponse.BodyHandlers.ofString()); if(r.statusCode()/100!=2) throw new IllegalStateException("Hacker News API returned HTTP "+r.statusCode()); return JSON.parseObject(r.body(), type); } catch(Exception e) { throw new IllegalStateException("Hacker News API request failed",e); } }
}
