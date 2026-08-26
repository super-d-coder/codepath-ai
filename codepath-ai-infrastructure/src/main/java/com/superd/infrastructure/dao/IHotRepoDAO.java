package com.superd.infrastructure.dao;

import com.superd.infrastructure.dao.po.HotRepoPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface IHotRepoDAO {
    int saveOrUpdate(HotRepoPO po);
    HotRepoPO queryById(@Param("id") long id);
    int deleteExpired(@Param("retentionDays") int retentionDays);
    List<HotRepoPO> queryLatest(@Param("language") String language, @Param("limit") int limit);
}
