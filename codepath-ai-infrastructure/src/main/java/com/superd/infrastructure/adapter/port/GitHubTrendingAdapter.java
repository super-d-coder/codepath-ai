package com.superd.infrastructure.adapter.port;

import com.superd.domain.infoflow.adapter.port.IGitHubTrendingPort;
import com.superd.domain.infoflow.model.entity.HotRepoEntity;
import com.superd.infrastructure.config.InfoFlowProperties;
import com.superd.infrastructure.gateway.dto.GitHubRepoDTO;
import com.superd.infrastructure.gateway.dto.GitHubReadmeDTO;
import com.superd.infrastructure.gateway.infoflow.GitHubApiClient;
import com.superd.infrastructure.gateway.infoflow.GitHubReadmeNormalizer;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class GitHubTrendingAdapter implements IGitHubTrendingPort {
    private final GitHubApiClient client;
    private final InfoFlowProperties properties;
    private final GitHubReadmeNormalizer readmeNormalizer;
    public GitHubTrendingAdapter(GitHubApiClient client, InfoFlowProperties properties, GitHubReadmeNormalizer readmeNormalizer) { this.client=client; this.properties=properties; this.readmeNormalizer=readmeNormalizer; }
    @Override public List<HotRepoEntity> fetchTrending() {
        String date=LocalDate.now().minusDays(properties.getGithub().getLookbackDays()).toString();
        var response=client.searchRepositories("created:>="+date);
        if(response==null || response.getItems()==null) return Collections.emptyList();
        LocalDateTime now=LocalDateTime.now();
        List<HotRepoEntity> entities = response.getItems().stream().map(r -> HotRepoEntity.builder().fullName(r.getFull_name()).name(r.getName()).description(r.getDescription()).htmlUrl(r.getHtml_url()).stars(r.getStargazers_count()).forks(r.getForks_count()).language(r.getLanguage()).topics(r.getTopics()==null?null:String.join(",",r.getTopics())).source("github").publishedAt(parse(r.getCreated_at())).collectedAt(now).build()).collect(Collectors.toList());
        Map<String, GitHubRepoDTO> repoByFullName = response.getItems().stream().collect(Collectors.toMap(GitHubRepoDTO::getFull_name, repo -> repo, (first, second) -> first));
        for (HotRepoEntity entity : entities) {
            try {
                GitHubReadmeDTO readme = client.getReadme(entity.getFullName());
                if (readme != null && readme.getContent() != null) {
                    String content = new String(Base64.getMimeDecoder().decode(readme.getContent()), StandardCharsets.UTF_8);
                    GitHubRepoDTO repo = repoByFullName.get(entity.getFullName());
                    String branch = repo == null || repo.getDefault_branch() == null || repo.getDefault_branch().isBlank() ? "main" : repo.getDefault_branch();
                    String normalized = readmeNormalizer.normalize(content, entity.getFullName(), branch, readme.getPath());
                    int maxChars = Math.max(1000, properties.getGithub().getReadmeMaxChars());
                    entity.setReadmeContent(normalized.substring(0, Math.min(normalized.length(), maxChars)));
                    entity.setReadmeUrl(readme.getHtml_url());
                    entity.setReadmeStatus("available");
                    entity.setReadmeFetchedAt(LocalDateTime.now());
                    log.info("InfoFlow github README 获取成功，仓库 {}", entity.getFullName());
                }
            } catch (Exception exception) {
                entity.setReadmeStatus("unavailable");
                log.warn("InfoFlow github README 获取失败，仓库 {}", entity.getFullName());
            }
        }
        return entities;
    }
    private LocalDateTime parse(String value) { try { return value==null?null:OffsetDateTime.parse(value).toLocalDateTime(); } catch(Exception e) { return null; } }
}
