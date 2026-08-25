package com.superd.domain.infoflow.adapter.repository;

import com.superd.domain.infoflow.model.entity.HotRepoEntity;

import java.util.List;

/**
 * 热门仓库仓储。
 */
public interface IHotRepoRepository {

    int saveOrUpdate(List<HotRepoEntity> entities);

    List<HotRepoEntity> queryLatest(String language, int limit);
}
