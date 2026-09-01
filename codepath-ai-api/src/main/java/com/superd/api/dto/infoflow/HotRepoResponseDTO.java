package com.superd.api.dto.infoflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 热门仓库响应 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotRepoResponseDTO {

    private Long id;
    private String fullName;
    private String name;
    private String description;
    private String htmlUrl;
    private Integer stars;
    private Integer forks;
    private String language;
    private String topics;
    private String source;
    private LocalDateTime publishedAt;
    private LocalDateTime collectedAt;
}
