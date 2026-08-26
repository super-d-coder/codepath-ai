package com.superd.infrastructure.gateway.infoflow;

import com.alibaba.fastjson.JSON;
import com.superd.infrastructure.config.InfoFlowProperties;
import com.superd.infrastructure.gateway.dto.*;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;


@Component
public class GitHubApiClient {
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final InfoFlowProperties properties;
    public GitHubApiClient(InfoFlowProperties properties) { this.properties = properties; }
    public GitHubReadmeDTO getReadme(String fullName) {
        String url = properties.getGithub().getApiBaseUrl() + "/repos/" + fullName + "/readme";
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
                .header("Accept", "application/vnd.github+json").header("User-Agent", "codepath-ai-infoflow");
        if (properties.getGithub().getToken() != null && !properties.getGithub().getToken().isBlank()) {
            builder.header("Authorization", "Bearer " + properties.getGithub().getToken());
        }
        try {
            HttpResponse<String> response = httpClient.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) throw new IllegalStateException("GitHub README API returned HTTP " + response.statusCode());
            return JSON.parseObject(response.body(), GitHubReadmeDTO.class);
        } catch (Exception e) {
            throw new IllegalStateException("GitHub README API request failed", e);
        }
    }

    public GitHubSearchResponseDTO searchRepositories(String query) {
        String url = properties.getGithub().getApiBaseUrl() + "/search/repositories?q=" + enc(query) + "&sort=stars&order=desc&per_page=" + properties.getGithub().getPerPage();
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).header("Accept", "application/vnd.github+json").header("User-Agent", "codepath-ai-infoflow");
        if (properties.getGithub().getToken() != null && !properties.getGithub().getToken().isBlank()) builder.header("Authorization", "Bearer " + properties.getGithub().getToken());
        try { HttpResponse<String> response = httpClient.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString()); if (response.statusCode() / 100 != 2) throw new IllegalStateException("GitHub API returned HTTP " + response.statusCode()); return JSON.parseObject(response.body(), GitHubSearchResponseDTO.class); }
        catch (Exception e) { throw new IllegalStateException("GitHub API request failed", e); }
    }
    private String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
