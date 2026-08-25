package com.superd.infrastructure.adapter.port;

import com.superd.domain.infoflow.adapter.port.IGitHubTrendingPort;
import com.superd.domain.infoflow.model.entity.HotRepoEntity;
import com.superd.infrastructure.config.InfoFlowProperties;
import com.superd.infrastructure.gateway.dto.GitHubRepoDTO;
import com.superd.infrastructure.gateway.infoflow.GitHubApiClient;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class GitHubTrendingAdapter implements IGitHubTrendingPort {
    private final GitHubApiClient client;
    private final InfoFlowProperties properties;
    public GitHubTrendingAdapter(GitHubApiClient client, InfoFlowProperties properties) { this.client=client; this.properties=properties; }
    @Override public List<HotRepoEntity> fetchTrending() {
        String date=LocalDate.now().minusDays(properties.getGithub().getLookbackDays()).toString();
        var response=client.searchRepositories("created:>="+date);
        if(response==null || response.getItems()==null) return Collections.emptyList();
        LocalDateTime now=LocalDateTime.now();
        return response.getItems().stream().map(r -> HotRepoEntity.builder().fullName(r.getFull_name()).name(r.getName()).description(r.getDescription()).htmlUrl(r.getHtml_url()).stars(r.getStargazers_count()).forks(r.getForks_count()).language(r.getLanguage()).topics(r.getTopics()==null?null:String.join(",",r.getTopics())).source("github").publishedAt(parse(r.getCreated_at())).collectedAt(now).build()).collect(Collectors.toList());
    }
    private LocalDateTime parse(String value) { try { return value==null?null:OffsetDateTime.parse(value).toLocalDateTime(); } catch(Exception e) { return null; } }
}
