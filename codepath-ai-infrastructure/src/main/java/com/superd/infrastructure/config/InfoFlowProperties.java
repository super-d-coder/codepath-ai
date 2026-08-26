package com.superd.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "infoflow")
public class InfoFlowProperties {
    private Github github = new Github();
    private HackerNews hackerNews = new HackerNews();
    private Schedule schedule = new Schedule();
    @Data public static class Github { private String apiBaseUrl = "https://api.github.com"; private String token = ""; private int lookbackDays = 14; private int perPage = 30; }
    @Data public static class HackerNews {
        private String apiBaseUrl = "https://hacker-news.firebaseio.com/v0";
        private int topStoryLimit = 30;
        private int connectTimeoutSeconds = 15;
        private int requestTimeoutSeconds = 30;
        private int maxRetries = 2;
        private int retryDelayMillis = 1000;
    }
    @Data public static class Schedule { private String githubCron = "0 0 0 */6 * ?"; private String newsCron = "0 0 0 */2 * ?"; }
}
