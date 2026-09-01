package com.superd.trigger.http;

import com.superd.api.IInfoFlowService;
import com.superd.api.dto.infoflow.CollectResponseDTO;
import com.superd.api.dto.infoflow.HotRepoResponseDTO;
import com.superd.api.dto.infoflow.HotRepoDetailResponseDTO;
import com.superd.api.dto.infoflow.NewsItemResponseDTO;
import com.superd.api.response.Response;
import com.superd.domain.infoflow.model.entity.HotRepoEntity;
import com.superd.domain.infoflow.model.entity.NewsItemEntity;
import com.superd.domain.infoflow.model.valobj.CollectResultVO;
import com.superd.domain.infoflow.service.IInfoFlowDomainService;
import com.superd.types.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** InfoFlow 查询与手动采集接口。 */
@Slf4j
@RestController
@RequestMapping("/api/v1/infoflow")
@CrossOrigin(origins = "*")
public class InfoFlowController implements IInfoFlowService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final IInfoFlowDomainService infoFlowDomainService;

    public InfoFlowController(IInfoFlowDomainService infoFlowDomainService) {
        this.infoFlowDomainService = infoFlowDomainService;
    }

    @Override
    @GetMapping("/repos")
    public Response<List<HotRepoResponseDTO>> queryHotRepos(
            @RequestParam(name = "language", required = false) String language,
            @RequestParam(name = "limit", required = false) Integer limit) {
        int safeLimit = normalizeLimit(limit);
        List<HotRepoResponseDTO> data = infoFlowDomainService.queryHotRepos(language, safeLimit)
                .stream().map(this::toHotRepoDTO).collect(Collectors.toList());
        return success(data);
    }

    @Override
    @GetMapping("/repos/{id}")
    public Response<HotRepoDetailResponseDTO> queryHotRepoDetail(@PathVariable("id") Long id) {
        HotRepoEntity entity = infoFlowDomainService.queryHotRepoDetail(id);
        if (entity == null) return failure("仓库不存在");
        HotRepoResponseDTO base = toHotRepoDTO(entity);
        HotRepoDetailResponseDTO detail = new HotRepoDetailResponseDTO();
        org.springframework.beans.BeanUtils.copyProperties(base, detail);
        detail.setReadmeContent(entity.getReadmeContent());
        detail.setReadmeUrl(entity.getReadmeUrl());
        detail.setReadmeStatus(entity.getReadmeStatus());
        detail.setReadmeFetchedAt(entity.getReadmeFetchedAt());
        return success(detail);
    }

    @Override
    @GetMapping("/news")
    public Response<List<NewsItemResponseDTO>> queryNews(
            @RequestParam(name = "source", required = false) String source,
            @RequestParam(name = "limit", required = false) Integer limit) {
        int safeLimit = normalizeLimit(limit);
        List<NewsItemResponseDTO> data = infoFlowDomainService.queryNews(source, safeLimit)
                .stream().map(this::toNewsItemDTO).collect(Collectors.toList());
        return success(data);
    }

    @Override
    @PostMapping("/collect/github")
    public Response<CollectResponseDTO> collectGitHub() {
        return collect(infoFlowDomainService::collectGitHubTrending);
    }

    @Override
    @PostMapping("/collect/news")
    public Response<CollectResponseDTO> collectNews() {
        return collect(infoFlowDomainService::collectIndustryNews);
    }

    @Override
    @PostMapping("/collect/all")
    public Response<List<CollectResponseDTO>> collectAll() {
        List<CollectResponseDTO> results = new ArrayList<>();
        results.add(collectSafely("github", infoFlowDomainService::collectGitHubTrending));
        results.add(collectSafely("hacker_news", infoFlowDomainService::collectIndustryNews));
        return success(results);
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }

    private Response<CollectResponseDTO> collect(Supplier<CollectResultVO> action) {
        try {
            return success(toCollectDTO(action.get()));
        } catch (Exception exception) {
            log.error("InfoFlow 手动采集失败", exception);
            return failure(exception.getMessage());
        }
    }

    private CollectResponseDTO collectSafely(String source, Supplier<CollectResultVO> action) {
        try {
            return toCollectDTO(action.get());
        } catch (Exception exception) {
            log.error("InfoFlow {} 采集失败，继续执行后续来源", source, exception);
            return CollectResponseDTO.builder()
                    .source(source)
                    .fetchedCount(0)
                    .savedCount(0)
                    .success(false)
                    .message(exception.getMessage())
                    .collectedAt(LocalDateTime.now())
                    .build();
        }
    }

    private HotRepoResponseDTO toHotRepoDTO(HotRepoEntity entity) {
        return HotRepoResponseDTO.builder()
                .id(entity.getId())
                .fullName(entity.getFullName())
                .name(entity.getName())
                .description(entity.getDescription())
                .htmlUrl(entity.getHtmlUrl())
                .stars(entity.getStars())
                .forks(entity.getForks())
                .language(entity.getLanguage())
                .topics(entity.getTopics())
                .source(entity.getSource())
                .publishedAt(entity.getPublishedAt())
                .collectedAt(entity.getCollectedAt())
                .build();
    }

    private NewsItemResponseDTO toNewsItemDTO(NewsItemEntity entity) {
        return NewsItemResponseDTO.builder()
                .id(entity.getId())
                .externalId(entity.getExternalId())
                .title(entity.getTitle())
                .url(entity.getUrl())
                .author(entity.getAuthor())
                .summary(entity.getSummary())
                .source(entity.getSource())
                .publishedAt(entity.getPublishedAt())
                .collectedAt(entity.getCollectedAt())
                .build();
    }

    private CollectResponseDTO toCollectDTO(CollectResultVO result) {
        return CollectResponseDTO.builder()
                .source(result.getSource())
                .fetchedCount(result.getFetchedCount())
                .savedCount(result.getSavedCount())
                .success(result.isSuccess())
                .message(result.getMessage())
                .collectedAt(result.getCollectedAt())
                .build();
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }

    private <T> Response<T> failure(String message) {
        return Response.<T>builder()
                .code(ResponseCode.UN_ERROR.getCode())
                .info(message == null ? ResponseCode.UN_ERROR.getInfo() : message)
                .build();
    }
}
