package com.superd.infrastructure.adapter.repository;

import com.superd.domain.infoflow.adapter.repository.IHotRepoRepository;
import com.superd.domain.infoflow.model.entity.HotRepoEntity;
import com.superd.infrastructure.dao.IHotRepoDAO;
import com.superd.infrastructure.dao.po.HotRepoPO;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Repository
public class HotRepoRepository implements IHotRepoRepository {
    private final IHotRepoDAO dao;
    public HotRepoRepository(IHotRepoDAO dao) { this.dao=dao; }
    @Override public int saveOrUpdate(List<HotRepoEntity> entities) { if(entities==null || entities.isEmpty()) return 0; return entities.stream().map(this::toPO).mapToInt(dao::saveOrUpdate).sum(); }
    @Override public List<HotRepoEntity> queryLatest(String language, int limit) { return dao.queryLatest(language, limit).stream().map(this::toEntity).collect(Collectors.toList()); }
    private HotRepoPO toPO(HotRepoEntity e) { return HotRepoPO.builder().id(e.getId()).fullName(e.getFullName()).name(e.getName()).description(e.getDescription()).htmlUrl(e.getHtmlUrl()).stars(e.getStars()).forks(e.getForks()).language(e.getLanguage()).topics(e.getTopics()).source(e.getSource()).publishedAt(e.getPublishedAt()).collectedDate((e.getCollectedAt()==null?java.time.LocalDateTime.now():e.getCollectedAt()).toLocalDate()).collectedAt(e.getCollectedAt()).build(); }
    private HotRepoEntity toEntity(HotRepoPO p) { return HotRepoEntity.builder().id(p.getId()).fullName(p.getFullName()).name(p.getName()).description(p.getDescription()).htmlUrl(p.getHtmlUrl()).stars(p.getStars()).forks(p.getForks()).language(p.getLanguage()).topics(p.getTopics()).source(p.getSource()).publishedAt(p.getPublishedAt()).collectedAt(p.getCollectedAt()).build(); }
}
