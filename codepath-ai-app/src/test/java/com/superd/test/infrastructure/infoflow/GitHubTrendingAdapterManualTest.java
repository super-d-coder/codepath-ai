package com.superd.test.infrastructure.infoflow;

import com.superd.domain.infoflow.model.entity.HotRepoEntity;
import com.superd.infrastructure.adapter.port.infoflow.GitHubTrendingAdapter;
import com.superd.infrastructure.config.InfoFlowProperties;
import com.superd.infrastructure.gateway.infoflow.GitHubApiClient;
import com.superd.infrastructure.gateway.infoflow.GitHubReadmeNormalizer;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * GitHub 真实 API 手动验证。
 * 该测试访问外网，不启动 Spring、不写 MySQL；只在明确指定测试类时执行。
 */
public class GitHubTrendingAdapterManualTest {

    @Test
    public void test_fetchTrending_shouldGetRealRepositories() {
        InfoFlowProperties properties = new InfoFlowProperties();
        GitHubApiClient client = new GitHubApiClient(properties);
        GitHubTrendingAdapter adapter = new GitHubTrendingAdapter(client, properties, new GitHubReadmeNormalizer());

        List<HotRepoEntity> repos = adapter.fetchTrending();

        Assert.assertNotNull("GitHub 返回结果不能为 null", repos);
        Assert.assertFalse("GitHub 未返回任何仓库，可能是网络、限流或接口异常", repos.isEmpty());
        HotRepoEntity first = repos.get(0);
        Assert.assertNotNull("仓库 fullName 不能为空", first.getFullName());
        Assert.assertFalse("仓库 fullName 不能为空", first.getFullName().isBlank());
        Assert.assertNotNull("仓库 name 不能为空", first.getName());
        Assert.assertNotNull("仓库 htmlUrl 不能为空", first.getHtmlUrl());
        Assert.assertEquals("github", first.getSource());
        Assert.assertNotNull("collectedAt 不能为空", first.getCollectedAt());

        System.out.printf("GitHub InfoFlow 获取 %d 条仓库，首条：%s, stars=%s%n",
                repos.size(), first.getFullName(), first.getStars());
    }
}
