package com.superd.domain.infoflow.service;

import com.superd.domain.infoflow.adapter.port.IGitHubTrendingPort;
import com.superd.domain.infoflow.adapter.port.IIndustryNewsPort;
import com.superd.domain.infoflow.adapter.repository.IHotRepoRepository;
import com.superd.domain.infoflow.adapter.repository.INewsItemRepository;
import com.superd.domain.infoflow.model.entity.HotRepoEntity;
import com.superd.domain.infoflow.model.entity.NewsItemEntity;
import com.superd.domain.infoflow.model.valobj.CollectResultVO;
import com.superd.domain.infoflow.model.valobj.SourceTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/** InfoFlow 领域服务实现。 */
@Slf4j
@Service
public class InfoFlowService implements IInfoFlowDomainService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final IGitHubTrendingPort gitHubTrendingPort;
    private final IIndustryNewsPort industryNewsPort;
    private final IHotRepoRepository hotRepoRepository;
    private final INewsItemRepository newsItemRepository;

    public InfoFlowService(IGitHubTrendingPort gitHubTrendingPort,
                           IIndustryNewsPort industryNewsPort,
                           IHotRepoRepository hotRepoRepository,
                           INewsItemRepository newsItemRepository) {
        this.gitHubTrendingPort = gitHubTrendingPort;
        this.industryNewsPort = industryNewsPort;
        this.hotRepoRepository = hotRepoRepository;
        this.newsItemRepository = newsItemRepository;
    }

    @Override
    public CollectResultVO collectGitHubTrending() {
        log.info("InfoFlow github 采集开始");
        return collect(SourceTypeEnum.GITHUB, () -> {
            List<HotRepoEntity> entities = safeList(gitHubTrendingPort.fetchTrending());
            log.info("InfoFlow github 数据获取完成，获取 {} 条", entities.size());
            entities.forEach(entity -> applyDefaults(entity, SourceTypeEnum.GITHUB.getCode()));
            int saved = hotRepoRepository.saveOrUpdate(entities);
            int deleted = entities.isEmpty() ? 0 : hotRepoRepository.deleteExpired();
            if (!entities.isEmpty()) {
                log.info("InfoFlow github 旧数据清理完成，删除 {} 条", deleted);
            } else {
                log.warn("InfoFlow github 返回空数据，本次不清理旧数据");
            }
            log.info("InfoFlow github 采集成功，获取 {} 条，保存 {} 条", entities.size(), saved);
            return result(SourceTypeEnum.GITHUB.getCode(), entities.size(), saved, true, "GitHub 热门仓库采集成功");
        });
    }

    @Override
    public CollectResultVO collectIndustryNews() {
        log.info("InfoFlow hacker_news 采集开始");
        return collect(SourceTypeEnum.HACKER_NEWS, () -> {
            List<NewsItemEntity> entities = safeList(industryNewsPort.fetchTopStories());
            log.info("InfoFlow hacker_news 数据获取完成，获取 {} 条", entities.size());
            entities.forEach(entity -> applyDefaults(entity, SourceTypeEnum.HACKER_NEWS.getCode()));
            int saved = newsItemRepository.saveIgnoreDuplicate(entities);
            log.info("InfoFlow hacker_news 采集成功，获取 {} 条，保存 {} 条", entities.size(), saved);
            return result(SourceTypeEnum.HACKER_NEWS.getCode(), entities.size(), saved, true, "行业资讯采集成功");
        });
    }

    @Override
    public HotRepoEntity queryHotRepoDetail(long id) {
        return hotRepoRepository.queryById(id);
    }

    @Override
    public List<HotRepoEntity> queryHotRepos(String language, int limit) {
        return hotRepoRepository.queryLatest(language, normalizeLimit(limit));
    }

    @Override
    public List<NewsItemEntity> queryNews(String source, int limit) {
        return newsItemRepository.queryLatest(source, normalizeLimit(limit));
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }

    private void applyDefaults(HotRepoEntity entity, String source) {
        if (entity != null) {
            if (entity.getSource() == null || entity.getSource().isBlank()) entity.setSource(source);
            if (entity.getCollectedAt() == null) entity.setCollectedAt(LocalDateTime.now());
        }
    }

    private void applyDefaults(NewsItemEntity entity, String source) {
        if (entity != null) {
            if (entity.getSource() == null || entity.getSource().isBlank()) entity.setSource(source);
            if (entity.getCollectedAt() == null) entity.setCollectedAt(LocalDateTime.now());
        }
    }

    private int normalizeLimit(int limit) {
        return limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
    }

    private CollectResultVO result(String source, int fetched, int saved, boolean success, String message) {
        return CollectResultVO.builder().source(source).fetchedCount(fetched).savedCount(saved)
                .success(success).message(message).collectedAt(LocalDateTime.now()).build();
    }

    private CollectResultVO collect(SourceTypeEnum source, Collector collector) {
        try {
            return collector.collect();
        } catch (Exception exception) {
            log.error("InfoFlow {} 采集失败", source.getCode(), exception);
            return result(source.getCode(), 0, 0, false,
                    exception.getMessage() == null || exception.getMessage().isBlank() ? "采集失败" : exception.getMessage());
        }
    }

    @FunctionalInterface
    private interface Collector {
        CollectResultVO collect();
    }
}
