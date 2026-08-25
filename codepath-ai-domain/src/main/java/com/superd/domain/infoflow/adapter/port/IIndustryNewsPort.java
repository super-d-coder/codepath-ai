package com.superd.domain.infoflow.adapter.port;

import com.superd.domain.infoflow.model.entity.NewsItemEntity;

import java.util.List;

/**
 * 行业资讯采集端口。
 */
public interface IIndustryNewsPort {

    List<NewsItemEntity> fetchTopStories();
}
