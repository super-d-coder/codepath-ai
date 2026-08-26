package com.superd.domain.infoflow.service;

import com.superd.domain.infoflow.model.entity.HotRepoEntity;
import com.superd.domain.infoflow.model.entity.NewsItemEntity;
import com.superd.domain.infoflow.model.valobj.CollectResultVO;

import java.util.List;

/**
 * InfoFlow 领域服务。
 */
public interface IInfoFlowDomainService {

    CollectResultVO collectGitHubTrending();

    CollectResultVO collectIndustryNews();

    List<HotRepoEntity> queryHotRepos(String language, int limit);

    HotRepoEntity queryHotRepoDetail(long id);

    List<NewsItemEntity> queryNews(String source, int limit);
}
