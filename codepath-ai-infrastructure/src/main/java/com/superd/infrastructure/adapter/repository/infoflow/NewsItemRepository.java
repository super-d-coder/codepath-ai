package com.superd.infrastructure.adapter.repository.infoflow;

import com.superd.domain.infoflow.adapter.repository.INewsItemRepository;
import com.superd.domain.infoflow.model.entity.NewsItemEntity;
import com.superd.infrastructure.dao.INewsItemDAO;
import com.superd.infrastructure.dao.po.NewsItemPO;
import org.springframework.stereotype.Repository;
import java.util.*;
import java.util.stream.Collectors;

@Repository
public class NewsItemRepository implements INewsItemRepository {
    private final INewsItemDAO dao;
    public NewsItemRepository(INewsItemDAO dao) { this.dao=dao; }
    @Override public int saveIgnoreDuplicate(List<NewsItemEntity> entities) { if(entities==null || entities.isEmpty()) return 0; return entities.stream().map(this::toPO).mapToInt(dao::saveIgnoreDuplicate).sum(); }
    @Override public List<NewsItemEntity> queryLatest(String source, int limit) { return dao.queryLatest(source, limit).stream().map(this::toEntity).collect(Collectors.toList()); }
    private NewsItemPO toPO(NewsItemEntity e) { return NewsItemPO.builder().id(e.getId()).externalId(e.getExternalId()).title(e.getTitle()).url(e.getUrl()).author(e.getAuthor()).summary(e.getSummary()).source(e.getSource()).publishedAt(e.getPublishedAt()).collectedAt(e.getCollectedAt()).build(); }
    private NewsItemEntity toEntity(NewsItemPO p) { return NewsItemEntity.builder().id(p.getId()).externalId(p.getExternalId()).title(p.getTitle()).url(p.getUrl()).author(p.getAuthor()).summary(p.getSummary()).source(p.getSource()).publishedAt(p.getPublishedAt()).collectedAt(p.getCollectedAt()).build(); }
}
