package com.superd.domain.infoflow.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 信息采集结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectResultVO {

    private String source;
    private Integer fetchedCount;
    private Integer savedCount;
    private boolean success;
    private String message;
    private LocalDateTime collectedAt;
}
