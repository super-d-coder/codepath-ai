package com.superd.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 行业资讯响应 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsItemResponseDTO {

    private Long id;
    private String externalId;
    private String title;
    private String url;
    private String author;
    private String summary;
    private String source;
    private LocalDateTime publishedAt;
    private LocalDateTime collectedAt;
}
