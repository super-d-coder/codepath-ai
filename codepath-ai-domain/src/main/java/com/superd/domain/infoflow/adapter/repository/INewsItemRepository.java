package com.superd.domain.infoflow.adapter.repository;

import com.superd.domain.infoflow.model.entity.NewsItemEntity;

import java.util.List;

/**
 * 行业资讯仓储。
 */
public interface INewsItemRepository {

    int saveIgnoreDuplicate(List<NewsItemEntity> entities);

    List<NewsItemEntity> queryLatest(String source, int limit);
}
