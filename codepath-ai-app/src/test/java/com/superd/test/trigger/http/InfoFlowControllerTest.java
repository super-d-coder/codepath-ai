package com.superd.test.trigger.http;

import com.superd.api.dto.infoflow.CollectResponseDTO;
import com.superd.api.dto.infoflow.HotRepoResponseDTO;
import com.superd.api.dto.infoflow.NewsItemResponseDTO;
import com.superd.api.response.Response;
import com.superd.domain.infoflow.model.entity.HotRepoEntity;
import com.superd.domain.infoflow.model.entity.NewsItemEntity;
import com.superd.domain.infoflow.model.valobj.CollectResultVO;
import com.superd.domain.infoflow.service.IInfoFlowDomainService;
import com.superd.trigger.http.InfoFlowController;
import com.superd.types.enums.ResponseCode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class InfoFlowControllerTest {

    @Mock
    private IInfoFlowDomainService infoFlowDomainService;

    private InfoFlowController controller;

    @Before
    public void setUp() {
        controller = new InfoFlowController(infoFlowDomainService);
    }

    @Test
    public void test_queryHotRepos_shouldReturnApiDTOAndDefaultLimit() {
        HotRepoEntity entity = HotRepoEntity.builder().id(1L).fullName("acme/demo").name("demo").source("github").build();
        when(infoFlowDomainService.queryHotRepos(null, 20)).thenReturn(List.of(entity));

        Response<List<HotRepoResponseDTO>> response = controller.queryHotRepos(null, null);

        assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        assertEquals(1, response.getData().size());
        assertEquals("acme/demo", response.getData().get(0).getFullName());
        verify(infoFlowDomainService).queryHotRepos(null, 20);
    }

    @Test
    public void test_queryNews_shouldClampLimitAndReturnApiDTO() {
        NewsItemEntity entity = NewsItemEntity.builder().externalId("100").title("A story").url("https://example.com").source("hacker_news").build();
        when(infoFlowDomainService.queryNews("hacker_news", 100)).thenReturn(List.of(entity));

        Response<List<NewsItemResponseDTO>> response = controller.queryNews("hacker_news", 200);

        assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        assertEquals("100", response.getData().get(0).getExternalId());
        assertEquals("A story", response.getData().get(0).getTitle());
        verify(infoFlowDomainService).queryNews("hacker_news", 100);
    }

    @Test
    public void test_collectAll_shouldContinueAfterOneFailedResult() {
        when(infoFlowDomainService.collectGitHubTrending()).thenReturn(CollectResultVO.builder()
                .source("github").success(false).message("rate limited").build());
        when(infoFlowDomainService.collectIndustryNews()).thenReturn(CollectResultVO.builder()
                .source("hacker_news").success(true).fetchedCount(1).savedCount(1).build());

        Response<List<CollectResponseDTO>> response = controller.collectAll();

        assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        assertEquals(2, response.getData().size());
        assertFalse(response.getData().get(0).isSuccess());
        assertTrue(response.getData().get(1).isSuccess());
        verify(infoFlowDomainService).collectGitHubTrending();
        verify(infoFlowDomainService).collectIndustryNews();
    }

    @Test
    public void test_collectGithub_shouldMapCollectResult() {
        when(infoFlowDomainService.collectGitHubTrending()).thenReturn(CollectResultVO.builder()
                .source("github").fetchedCount(3).savedCount(2).success(true).message("ok").build());

        Response<CollectResponseDTO> response = controller.collectGitHub();

        assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        assertEquals(Integer.valueOf(3), response.getData().getFetchedCount());
        assertEquals(Integer.valueOf(2), response.getData().getSavedCount());
        assertTrue(response.getData().isSuccess());
    }
}
