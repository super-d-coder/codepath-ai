package com.superd.infrastructure.adapter.port.infoflow;

import com.superd.domain.infoflow.adapter.port.IIndustryNewsPort;
import com.superd.domain.infoflow.model.entity.NewsItemEntity;
import com.superd.infrastructure.config.InfoFlowProperties;
import com.superd.infrastructure.gateway.dto.HackerNewsItemDTO;
import com.superd.infrastructure.gateway.infoflow.HackerNewsApiClient;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class HackerNewsAdapter implements IIndustryNewsPort {
    private final HackerNewsApiClient client; private final InfoFlowProperties properties;
    public HackerNewsAdapter(HackerNewsApiClient client, InfoFlowProperties properties) { this.client=client; this.properties=properties; }
    @Override public List<NewsItemEntity> fetchTopStories() {
        List<Long> ids=client.topStoryIds(); if(ids==null) return Collections.emptyList();
        return ids.stream().limit(properties.getHackerNews().getTopStoryLimit()).map(client::item).filter(i -> i!=null && "story".equals(i.getType()) && i.getTitle()!=null && !i.getTitle().isBlank()).map(this::toEntity).collect(Collectors.toList());
    }
    private NewsItemEntity toEntity(HackerNewsItemDTO i) { return NewsItemEntity.builder().externalId(String.valueOf(i.getId())).title(i.getTitle()).url(i.getUrl()==null?"https://news.ycombinator.com/item?id="+i.getId():i.getUrl()).author(i.getBy()).source("hacker_news").publishedAt(i.getTime()==null?null:LocalDateTime.ofInstant(Instant.ofEpochSecond(i.getTime()),ZoneId.systemDefault())).collectedAt(LocalDateTime.now()).build(); }
}
