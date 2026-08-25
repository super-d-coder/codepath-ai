package com.superd.test.domain.infoflow;

import com.superd.domain.infoflow.adapter.port.IGitHubTrendingPort;
import com.superd.domain.infoflow.adapter.port.IIndustryNewsPort;
import com.superd.domain.infoflow.adapter.repository.IHotRepoRepository;
import com.superd.domain.infoflow.adapter.repository.INewsItemRepository;
import com.superd.domain.infoflow.model.entity.HotRepoEntity;
import com.superd.domain.infoflow.model.entity.NewsItemEntity;
import com.superd.domain.infoflow.model.valobj.CollectResultVO;
import com.superd.domain.infoflow.service.InfoFlowService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class InfoFlowServiceTest {

    @Mock
    private IGitHubTrendingPort gitHubTrendingPort;
    @Mock
    private IIndustryNewsPort industryNewsPort;
    @Mock
    private IHotRepoRepository hotRepoRepository;
    @Mock
    private INewsItemRepository newsItemRepository;
    @InjectMocks
    private InfoFlowService infoFlowService;

    @Before
    public void setUp() {
        when(hotRepoRepository.saveOrUpdate(anyList())).thenReturn(1);
        when(newsItemRepository.saveIgnoreDuplicate(anyList())).thenReturn(1);
    }

    @Test
    public void test_collectGitHubTrending_shouldApplyDefaultsAndSave() {
        HotRepoEntity repo = HotRepoEntity.builder().fullName("acme/demo").name("demo").build();
        when(gitHubTrendingPort.fetchTrending()).thenReturn(List.of(repo));

        CollectResultVO result = infoFlowService.collectGitHubTrending();

        assertTrue(result.isSuccess());
        assertEquals("github", result.getSource());
        assertEquals(Integer.valueOf(1), result.getFetchedCount());
        assertEquals(Integer.valueOf(1), result.getSavedCount());
        assertNotNull(result.getCollectedAt());
        ArgumentCaptor<List<HotRepoEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(hotRepoRepository).saveOrUpdate(captor.capture());
        assertEquals("github", captor.getValue().get(0).getSource());
        assertNotNull(captor.getValue().get(0).getCollectedAt());
    }

    @Test
    public void test_collectIndustryNews_shouldApplyDefaultsAndSave() {
        NewsItemEntity news = NewsItemEntity.builder().externalId("100").title("A story").build();
        when(industryNewsPort.fetchTopStories()).thenReturn(List.of(news));

        CollectResultVO result = infoFlowService.collectIndustryNews();

        assertTrue(result.isSuccess());
        assertEquals("hacker_news", result.getSource());
        assertEquals(Integer.valueOf(1), result.getFetchedCount());
        verify(newsItemRepository).saveIgnoreDuplicate(anyList());
        assertEquals("hacker_news", news.getSource());
        assertNotNull(news.getCollectedAt());
    }

    @Test
    public void test_collectGitHubTrending_whenPortFails_shouldReturnFailure() {
        when(gitHubTrendingPort.fetchTrending()).thenThrow(new IllegalStateException("GitHub unavailable"));

        CollectResultVO result = infoFlowService.collectGitHubTrending();

        assertFalse(result.isSuccess());
        assertEquals(Integer.valueOf(0), result.getFetchedCount());
        assertEquals("GitHub unavailable", result.getMessage());
        verify(hotRepoRepository, never()).saveOrUpdate(anyList());
    }

    @Test
    public void test_queryHotRepos_shouldUseDefaultAndMaximumLimit() {
        when(hotRepoRepository.queryLatest("Java", 20)).thenReturn(List.of());
        when(hotRepoRepository.queryLatest("Java", 100)).thenReturn(List.of());

        infoFlowService.queryHotRepos("Java", 0);
        infoFlowService.queryHotRepos("Java", 200);

        verify(hotRepoRepository).queryLatest("Java", 20);
        verify(hotRepoRepository).queryLatest("Java", 100);
    }

    @Test
    public void test_queryNews_shouldDelegateSourceAndLimit() {
        when(newsItemRepository.queryLatest("hacker_news", 10)).thenReturn(List.of());

        infoFlowService.queryNews("hacker_news", 10);

        verify(newsItemRepository).queryLatest("hacker_news", 10);
    }
}
