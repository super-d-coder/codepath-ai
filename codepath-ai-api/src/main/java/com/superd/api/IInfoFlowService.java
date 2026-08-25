package com.superd.api;

import com.superd.api.dto.CollectResponseDTO;
import com.superd.api.dto.HotRepoResponseDTO;
import com.superd.api.dto.NewsItemResponseDTO;
import com.superd.api.response.Response;

import java.util.List;

/**
 * InfoFlow 对外服务接口。
 */
public interface IInfoFlowService {

    Response<List<HotRepoResponseDTO>> queryHotRepos(String language, Integer limit);

    Response<List<NewsItemResponseDTO>> queryNews(String source, Integer limit);

    Response<CollectResponseDTO> collectGitHub();

    Response<CollectResponseDTO> collectNews();

    Response<List<CollectResponseDTO>> collectAll();
}
