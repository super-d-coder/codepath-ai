package com.superd.domain.infoflow.adapter.port;

import com.superd.domain.infoflow.model.entity.HotRepoEntity;

import java.util.List;

/**
 * GitHub 热门仓库采集端口。
 */
public interface IGitHubTrendingPort {

    List<HotRepoEntity> fetchTrending();
}
