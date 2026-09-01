package com.superd.test.infrastructure.infoflow;

import com.superd.domain.infoflow.model.entity.NewsItemEntity;
import com.superd.infrastructure.adapter.port.infoflow.HackerNewsAdapter;
import com.superd.infrastructure.config.InfoFlowProperties;
import com.superd.infrastructure.gateway.infoflow.HackerNewsApiClient;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * Hacker News 真实 API 手动验证。
 * 该测试访问外网，不启动 Spring、不写 MySQL；只在明确指定测试类时执行。
 */
public class HackerNewsAdapterManualTest {

    @Test
    public void test_fetchTopStories_shouldGetRealNews() {
        InfoFlowProperties properties = new InfoFlowProperties();
        HackerNewsApiClient client = new HackerNewsApiClient(properties);
        HackerNewsAdapter adapter = new HackerNewsAdapter(client, properties);

        List<NewsItemEntity> news = adapter.fetchTopStories();

        Assert.assertNotNull("Hacker News 返回结果不能为 null", news);
        Assert.assertFalse("Hacker News 未返回任何资讯，可能是网络或接口异常", news.isEmpty());
        NewsItemEntity first = news.get(0);
        Assert.assertNotNull("externalId 不能为空", first.getExternalId());
        Assert.assertNotNull("title 不能为空", first.getTitle());
        Assert.assertFalse("title 不能为空", first.getTitle().isBlank());
        Assert.assertNotNull("url 不能为空", first.getUrl());
        Assert.assertEquals("hacker_news", first.getSource());
        Assert.assertNotNull("collectedAt 不能为空", first.getCollectedAt());

        System.out.printf("Hacker News InfoFlow 获取 %d 条资讯，首条：%s%n",
                news.size(), first.getTitle());
    }
}
