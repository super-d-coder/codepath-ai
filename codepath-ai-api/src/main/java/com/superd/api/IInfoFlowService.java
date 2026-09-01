package com.superd.api;

import com.superd.api.dto.infoflow.CollectResponseDTO;
import com.superd.api.dto.infoflow.HotRepoResponseDTO;
import com.superd.api.dto.infoflow.HotRepoDetailResponseDTO;
import com.superd.api.dto.infoflow.NewsItemResponseDTO;
import com.superd.api.response.Response;

import java.util.List;

/**
 * InfoFlow 对外服务接口。
 */
public interface IInfoFlowService {

    Response<List<HotRepoResponseDTO>> queryHotRepos(String language, Integer limit);

    Response<HotRepoDetailResponseDTO> queryHotRepoDetail(Long id);

    Response<List<NewsItemResponseDTO>> queryNews(String source, Integer limit);

    Response<CollectResponseDTO> collectGitHub();

    Response<CollectResponseDTO> collectNews();

    Response<List<CollectResponseDTO>> collectAll();
}
