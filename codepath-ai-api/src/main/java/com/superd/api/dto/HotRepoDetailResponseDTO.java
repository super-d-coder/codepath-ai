package com.superd.api.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

/** GitHub 仓库详情响应，包含 README Markdown。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class HotRepoDetailResponseDTO extends HotRepoResponseDTO {
    private String readmeContent;
    private String readmeUrl;
    private String readmeStatus;
    private LocalDateTime readmeFetchedAt;
}
