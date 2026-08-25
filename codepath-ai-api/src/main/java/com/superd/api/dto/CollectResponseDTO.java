package com.superd.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 采集操作响应 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectResponseDTO {

    private String source;
    private Integer fetchedCount;
    private Integer savedCount;
    private boolean success;
    private String message;
    private LocalDateTime collectedAt;
}
