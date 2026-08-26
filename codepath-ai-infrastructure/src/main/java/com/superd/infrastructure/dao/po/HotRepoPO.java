package com.superd.infrastructure.dao.po;

import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class HotRepoPO {
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
    private LocalDate collectedDate;
    private LocalDateTime collectedAt;
    private String readmeContent;
    private String readmeUrl;
    private String readmeStatus;
    private LocalDateTime readmeFetchedAt;
}
