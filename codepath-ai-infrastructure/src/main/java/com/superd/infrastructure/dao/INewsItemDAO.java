package com.superd.infrastructure.dao;

import com.superd.infrastructure.dao.po.NewsItemPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface INewsItemDAO {
    int saveIgnoreDuplicate(NewsItemPO po);
    List<NewsItemPO> queryLatest(@Param("source") String source, @Param("limit") int limit);
}
