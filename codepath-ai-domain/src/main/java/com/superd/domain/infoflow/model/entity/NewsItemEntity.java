package com.superd.domain.infoflow.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsItemEntity {

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
