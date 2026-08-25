package com.superd.infrastructure.dao.po;

import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class NewsItemPO {
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
