package com.superd.domain.infoflow.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * InfoFlow 数据来源类型。
 */
@Getter
@AllArgsConstructor
public enum SourceTypeEnum {

    GITHUB("github"),
    HACKER_NEWS("hacker_news");

    private final String code;
}
